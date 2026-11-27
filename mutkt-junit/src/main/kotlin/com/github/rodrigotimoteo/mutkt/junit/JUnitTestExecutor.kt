package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TimedOutcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.TagFilter
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request
import org.junit.platform.launcher.core.LauncherFactory
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal const val JUPITER_ENGINE = "junit-jupiter"

/**
 * Runs the project's tests of the selected [engines] (JUnit tag expressions in [includeTags] and [excludeTags]
 * narrow them; no include tags means all) in a fresh class loader per run that defines the project's classes (from
 * memory, the mutant included) and the [classpath], so dependencies resolve project classes but nothing outlives a run.
 * [codeSources] maps binary class names to the location their code source reports (where Gradle built them).
 *
 * Each run executes on its own daemon thread and the deadline starts once the tests are discovered.
 * Loading, linking and discovery are unbounded here: only a forked child limits them (ForkSettings.prepareMillis).
 * On timeout the thread is interrupted and abandoned: an uninterruptible loop keeps burning a core and its
 * class loader stays in memory until the JVM exits. Only a forked JVM can kill it for real (ROD-104).
 * A mutant that ignores interrupts is likewise abandoned but keeps running.
 */
@Suppress("LongParameterList")
public class JUnitTestExecutor(
    projectClasses: List<ClassFile>,
    testClasses: List<ClassFile>,
    classpath: List<Path>,
    engines: List<String> = listOf(JUPITER_ENGINE),
    includeTags: List<String> = emptyList(),
    excludeTags: List<String> = emptyList(),
    codeSources: Map<String, Path> = emptyMap(),
) : TestExecutor,
    AutoCloseable {
    init {
        require(engines.isNotEmpty()) { "The 'engines' setting must not be empty" }
    }

    private val classes = (projectClasses + testClasses).associate { it.name to it.bytes }
    private val testNames = testClasses.map { it.name }.sorted()
    private val filters =
        listOf(includeEngines(engines)) +
            listOfNotNull(
                includeTags.takeIf { it.isNotEmpty() }?.let(TagFilter::includeTags),
                excludeTags.takeIf { it.isNotEmpty() }?.let(TagFilter::excludeTags),
            )

    private val classpath = classpath.toList()
    private val codeSources = codeSources.toMap()

    @Volatile private var closed = false

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome = run(mutant, bytes, deadline) {}.outcome

    override fun baseline(deadline: Duration): TimedOutcome = baseline(deadline) {}

    // [onExecuting] runs once on the worker when the tests are discovered and the deadline is about to start; it is
    // skipped when the mutant or a test class fails to link. The fork protocol reports it as Started.
    internal fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        onExecuting: () -> Unit,
    ): TimedOutcome {
        val loaded = classes + (mutant.className to bytes)
        return launch(mutant.id.toString(), loaded, mutant.className, deadline, onExecuting)
    }

    internal fun baseline(
        deadline: Duration,
        onExecuting: () -> Unit,
    ): TimedOutcome = launch("the baseline", classes, null, deadline, onExecuting)

    // [linked] is the mutated class, linked before the tests run; null when nothing is replaced.
    private fun launch(
        label: String,
        loaded: Map<String, ByteArray>,
        linked: String?,
        deadline: Duration,
        onExecuting: () -> Unit,
    ): TimedOutcome {
        require(deadline.isPositive()) { "Deadline must be positive, was $deadline" }
        check(!closed) { "JUnitTestExecutor is closed, cannot run $label" }
        return projectLoader(classpath, JUnitTestExecutor::class.java.classLoader, loaded, codeSources).use { loader ->
            val started = CountDownLatch(1)
            val task =
                FutureTask {
                    // Launcher and engines discover and load through the context loader.
                    Thread.currentThread().contextClassLoader = loader
                    try {
                        execute(linked, loader) {
                            onExecuting()
                            started.countDown()
                        }
                    } finally {
                        started.countDown()
                    }
                }
            Thread(task, "mutkt-mutant-${label.hashCode()}")
                .apply { isDaemon = true }
                .start()
            try {
                // Loading, linking and discovery happen before the latch, so they are not charged to the deadline.
                started.await()
                val start = System.nanoTime()
                val outcome =
                    try {
                        task.get(deadline.inWholeNanoseconds, TimeUnit.NANOSECONDS)
                    } catch (_: TimeoutException) {
                        Outcome.Timeout
                    }
                TimedOutcome(outcome, (System.nanoTime() - start).nanoseconds)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            } finally {
                // No-op when finished; otherwise (timeout, caller interrupted) interrupts the worker and abandons it.
                task.cancel(true)
            }
        }
    }

    override fun close() {
        closed = true
    }

    private fun execute(
        linked: String?,
        loader: ClassLoader,
        executing: () -> Unit,
    ): Outcome {
        // A mutant or test class that cannot link is a broken input, not a kill.
        // declaredMethods links the mutant, which is what surfaces VerifyError before any test touches it.
        val types =
            try {
                linked?.let { loader.loadClass(it).declaredMethods }
                testNames.map(loader::loadClass).filter(::isRunnableRoot)
            } catch (e: LinkageError) {
                return Outcome.LoadError(e.toString())
            }
        return runTests(types, executing)
    }

    // @Nested classes run through their enclosing class; selecting them too would run them twice.
    private fun isRunnableRoot(type: Class<*>) =
        !type.isSynthetic &&
            !type.isAnonymousClass &&
            (type.enclosingClass == null || Modifier.isStatic(type.modifiers))

    private fun runTests(
        types: List<Class<*>>,
        executing: () -> Unit,
    ): Outcome {
        // A new launcher per run: nothing is cached between runs, and engines must be found via this loader.
        val launcher = LauncherFactory.create(LauncherConfig.builder().enableTestEngineAutoRegistration(true).build())
        // Discovery runs project code (display name generators, orderers): a stall there is not a detection.
        val plans = types.map { launcher.discover(request(it)) }
        executing()
        var testsStarted = 0
        // An abandoned (interrupted) worker must not run the remaining classes against a closed loader.
        for (plan in plans.asSequence().takeWhile { !Thread.currentThread().isInterrupted }) {
            val listener = OutcomeListener()
            launcher.execute(plan, listener)
            testsStarted += listener.started
            listener.killedBy?.let { return Outcome.Killed(it, listener.cause) }
        }
        return if (testsStarted == 0) Outcome.NoTests else Outcome.Survived
    }

    @Suppress("SpreadOperator")
    private fun request(type: Class<*>) =
        request()
            .selectors(selectClass(type))
            .filters(*filters.toTypedArray())
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .build()
}
