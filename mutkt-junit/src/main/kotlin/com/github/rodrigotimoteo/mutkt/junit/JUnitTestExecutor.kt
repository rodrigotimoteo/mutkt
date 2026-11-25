package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
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

internal const val JUPITER_ENGINE = "junit-jupiter"

/**
 * Runs the project's tests of the selected [engines] (JUnit tag expressions in [includeTags] and [excludeTags]
 * narrow them; no include tags means all) in isolated class loaders, one fresh set per mutant.
 *
 * Each run executes on its own daemon thread and the deadline starts when the launcher starts the tests.
 * On timeout the thread is interrupted and abandoned: an uninterruptible loop keeps burning a core and its
 * class loader stays in memory until the JVM exits. Only a forked JVM can kill it for real (ROD-104).
 * A mutant that ignores interrupts is likewise abandoned but keeps running.
 */
public class JUnitTestExecutor(
    projectClasses: List<ClassFile>,
    testClasses: List<ClassFile>,
    classpath: List<Path>,
    engines: List<String> = listOf(JUPITER_ENGINE),
    includeTags: List<String> = emptyList(),
    excludeTags: List<String> = emptyList(),
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

    // Dependency jars are shared by concurrent mutants through this loader (library statics included);
    // mutated and test classes are defined per run.
    private val base = projectLoader(classpath, JUnitTestExecutor::class.java.classLoader)

    @Volatile private var closed = false

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome = run(mutant, bytes, deadline) {}

    override fun baseline(deadline: Duration): Outcome = baseline(deadline) {}

    // [onExecuting] runs once on the worker when the launcher exists and the deadline is about to start; it is
    // skipped when the mutant or a test class fails to link. The fork protocol reports it as Started.
    internal fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        onExecuting: () -> Unit,
    ): Outcome {
        val loaded = classes + (mutant.className to bytes)
        return launch(mutant.id.toString(), loaded, mutant.className, deadline, onExecuting)
    }

    internal fun baseline(
        deadline: Duration,
        onExecuting: () -> Unit,
    ): Outcome = launch("the baseline", classes, null, deadline, onExecuting)

    // [linked] is the mutated class, linked before the tests run; null when nothing is replaced.
    private fun launch(
        label: String,
        loaded: Map<String, ByteArray>,
        linked: String?,
        deadline: Duration,
        onExecuting: () -> Unit,
    ): Outcome {
        require(deadline.isPositive()) { "Deadline must be positive, was $deadline" }
        check(!closed) { "JUnitTestExecutor is closed, cannot run $label" }
        return MutantClassLoader(base, loaded).use { loader ->
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
                // Class loading and linking happen before the latch, so they are not charged to the deadline.
                started.await()
                task.get(deadline.inWholeNanoseconds, TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                Outcome.Timeout
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
        base.close()
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
        executing()
        var testsStarted = 0
        // An abandoned (interrupted) worker must not run the remaining classes against a closed loader.
        for (type in types.asSequence().takeWhile { !Thread.currentThread().isInterrupted }) {
            val listener = OutcomeListener()
            launcher.execute(request(type), listener)
            testsStarted += listener.started
            listener.killedBy?.let { return Outcome.Killed(it) }
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
