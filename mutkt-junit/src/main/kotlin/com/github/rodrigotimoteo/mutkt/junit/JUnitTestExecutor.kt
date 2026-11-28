package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
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
 * A baseline on instrumented classes reads their probes after each test class (see [CoverageListener]).
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
    private val testNameSet = testNames.toSet()
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
        tests: List<TestId>?,
    ): Outcome = run(mutant, bytes, deadline, tests) {}.outcome

    override fun baseline(
        deadline: Duration,
        instrumented: Instrumented?,
    ): Baseline = baseline(deadline, instrumented) {}

    // [onExecuting] runs once on the worker when the tests are discovered and the deadline is about to start; it is
    // skipped when the mutant or a test class fails to link. The fork protocol reports it as Started.
    internal fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        tests: List<TestId>?,
        onExecuting: () -> Unit,
    ): TimedOutcome {
        val names = tests?.map { it.className }?.onEach { require(it in testNameSet) { "No test class $it" } }
        val request = Request(mutant.id.toString(), classes + (mutant.className to bytes), mutant.className, names)
        return launch(request, deadline, onExecuting).let { TimedOutcome(it.outcome, it.elapsed) }
    }

    internal fun baseline(
        deadline: Duration,
        instrumented: Instrumented?,
        onExecuting: () -> Unit,
    ): Baseline {
        val probed = instrumented?.classes.orEmpty().associate { it.name to it.bytes }
        return launch(Request("the baseline", classes + probed, instrumented = instrumented), deadline, onExecuting)
    }

    private fun launch(
        request: Request,
        deadline: Duration,
        onExecuting: () -> Unit,
    ): Baseline {
        require(deadline.isPositive()) { "Deadline must be positive, was $deadline" }
        check(!closed) { "JUnitTestExecutor is closed, cannot run ${request.label}" }
        val (label, loaded) = request
        return projectLoader(classpath, JUnitTestExecutor::class.java.classLoader, loaded, codeSources).use { loader ->
            val started = CountDownLatch(1)
            val task =
                FutureTask {
                    // Launcher and engines discover and load through the context loader.
                    Thread.currentThread().contextClassLoader = loader
                    try {
                        execute(request, loader) {
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
                val (outcome, coverage) =
                    try {
                        task.get(deadline.inWholeNanoseconds, TimeUnit.NANOSECONDS)
                    } catch (_: TimeoutException) {
                        Outcome.Timeout to null
                    }
                Baseline(outcome, (System.nanoTime() - start).nanoseconds, coverage)
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

    // The coverage is null unless the request has instrumented classes.
    private fun execute(
        request: Request,
        loader: MutantClassLoader,
        executing: () -> Unit,
    ): Pair<Outcome, Coverage?> {
        // A mutant or test class that cannot link is a broken input, not a kill.
        // declaredMethods links the mutant, which is what surfaces VerifyError before any test touches it.
        val types =
            try {
                request.linked?.let { loader.loadClass(it).declaredMethods }
                (request.tests ?: testNames).map(loader::loadClass).filter(::isRunnableRoot)
            } catch (e: LinkageError) {
                return Outcome.LoadError(e.toString()) to null
            }
        val coverage =
            request.instrumented?.let { instrumented ->
                CoverageListener(ProbeReader(instrumented.classes.map { it.name }))
            }
        return runTests(types, executing, coverage) to coverage?.coverage()
    }

    // @Nested classes run through their enclosing class; selecting them too would run them twice.
    private fun isRunnableRoot(type: Class<*>) =
        !type.isSynthetic &&
            !type.isAnonymousClass &&
            (type.enclosingClass == null || Modifier.isStatic(type.modifiers))

    private fun runTests(
        types: List<Class<*>>,
        executing: () -> Unit,
        coverage: CoverageListener?,
    ): Outcome {
        // A new launcher per run: nothing is cached between runs, and engines must be found via this loader.
        val launcher = LauncherFactory.create(LauncherConfig.builder().enableTestEngineAutoRegistration(true).build())
        // Discovery runs project code (display name generators, orderers): a stall there is not a detection.
        val plans = types.map { launcher.discover(request(it)) }
        executing()
        var testsStarted = 0
        // An abandoned (interrupted) worker must not run the remaining classes against a closed loader.
        for ((type, plan) in types.zip(plans).asSequence().takeWhile { !Thread.currentThread().isInterrupted }) {
            val listener = OutcomeListener()
            coverage?.testClass = type.name
            if (coverage == null) launcher.execute(plan, listener) else launcher.execute(plan, listener, coverage)
            testsStarted += listener.started
            listener.killedBy?.let { return Outcome.Killed(it, listener.kind, listener.cause) }
        }
        return if (testsStarted == 0) Outcome.NoTests else Outcome.Survived
    }

    // [linked] is the mutated class, linked before the tests run; null when nothing is replaced. [tests] are the test
    // classes to run, in order (all when null); probes are read only with [instrumented].
    private data class Request(
        val label: String,
        val loaded: Map<String, ByteArray>,
        val linked: String? = null,
        val tests: List<String>? = null,
        val instrumented: Instrumented? = null,
    )

    @Suppress("SpreadOperator")
    private fun request(type: Class<*>) =
        request()
            .selectors(selectClass(type))
            .filters(*filters.toTypedArray())
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .build()
}
