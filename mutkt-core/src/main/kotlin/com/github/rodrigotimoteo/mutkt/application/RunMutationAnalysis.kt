package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.CoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Discovers mutants, runs the tests that reach each one (all of them when coverage cannot tell) and writes the report.
 * Holds no state between runs.
 */
public class RunMutationAnalysis(
    private val classSource: ClassSource,
    private val generator: MutantGenerator,
    private val instrumenter: CoverageInstrumenter,
    private val executor: TestExecutor,
    private val writer: ReportWriter,
    private val config: AnalysisConfig,
) {
    /**
     * Runs the analysis; [onDeadlineCap] is called once before any mutant when timeoutMs cuts the derived deadline,
     * [onWarning] when the instrumented classes fail the tests the original ones pass, so coverage is not used,
     * [progress] on the calling thread once per finished mutant, in completion order.
     */
    public fun run(
        onDeadlineCap: (DeadlineCap) -> Unit = {},
        onWarning: (String) -> Unit = {},
        progress: (MutantResult) -> Unit = {},
    ): MutationReport {
        val filters = config.filters
        val classes = classSource.classes()
        val targets = classes.filter { filters.acceptsClass(it.name) }
        val (mutants, omitted) =
            filters.limit(
                generator.discover(classes, targets).filter { filters.acceptsMethod(it.className, it.methodName) },
            )
        if (mutants.isEmpty()) return MutationReport(emptyList(), omitted).also(writer::write)
        val instrumented = instrumenter.instrument(classes, mutants)
        // Without a green suite every failing test would count as a kill, so nothing is scored.
        val baseline = baseline(instrumented, onWarning)
        // An endless loop is cut soon after the suite's own time instead of at timeoutMs, which stays the upper bound.
        val needed = neededDeadline(baseline.elapsed)
        val cap =
            DeadlineCap(config.timeout, baseline.elapsed, needed)
                .takeIf { needed > config.timeout }
                ?.also(onDeadlineCap)
        val map = CoverageMap(instrumented.probes, baseline.coverage, config.timeout, minOf(config.timeout, needed))
        val originals = classes.associate { it.name to it.bytes }
        val results = analyse(mutants, map, Confirmation(originals), progress)
        return MutationReport(results, omitted, cap).also(writer::write)
    }

    // The green baseline, with coverage when the instrumented classes pass too. Probes must not change behavior, but if
    // they do, the original classes decide whether the suite is green.
    private fun baseline(
        instrumented: Instrumented,
        onWarning: (String) -> Unit,
    ): Baseline {
        val probed = greenBaseline(instrumented)
        probed.getOrNull()?.let { return it }
        val plain = greenBaseline(null).getOrElse { throw IllegalStateException("$BASELINE_FAILED: ${it.message}", it) }
        onWarning("The baseline on probed classes failed (${probed.exceptionOrNull()?.message}); $COVERAGE_LOST")
        return plain
    }

    // The baseline when the suite is green, else a failure that says why it is not.
    private fun greenBaseline(instrumented: Instrumented?): Result<Baseline> {
        val deadline = config.timeout * BASELINE_TIMEOUT_FACTOR
        val baseline =
            try {
                executor.baseline(deadline, instrumented)
            } catch (e: IllegalStateException) {
                return Result.failure(e)
            }
        return problem(baseline.outcome, deadline)?.let { Result.failure(IllegalStateException(it)) }
            ?: Result.success(baseline)
    }

    // Results are keyed by discovery index so completion order never leaks into the report.
    private fun analyse(
        mutants: List<Mutant>,
        map: CoverageMap,
        confirmation: Confirmation,
        progress: (MutantResult) -> Unit,
    ): List<MutantResult> {
        val pool = Executors.newFixedThreadPool(config.parallelism)
        try {
            val completion = ExecutorCompletionService<IndexedValue<MutantResult>>(pool)
            for ((index, mutant) in mutants.withIndex()) {
                completion.submit(
                    Callable { IndexedValue(index, evaluate(mutant, map.selection(mutant), confirmation)) },
                )
            }
            val byIndex = mutableMapOf<Int, MutantResult>()
            repeat(mutants.size) {
                val (index, result) = completion.take().getOrRethrowCause()
                byIndex[index] = result
                progress(result)
            }
            return mutants.indices.map { byIndex.getValue(it) }
        } finally {
            pool.stopAndAwait()
        }
    }

    // A mutant can make the JVM refuse its class (LinkageError), which is that mutant's ERROR. Any other Error, or an
    // executor that cannot continue, means the run itself is broken and must surface.
    @Suppress("TooGenericExceptionCaught")
    private fun evaluate(
        mutant: Mutant,
        selection: Selection,
        confirmation: Confirmation,
    ): MutantResult {
        val covering = selection.tests?.map { it.className }
        return try {
            if (covering?.isEmpty() == true) {
                MutantResult(mutant, MutantStatus.NO_COVERAGE, coveredBy = covering)
            } else {
                val outcome = executor.run(mutant, generator.materialize(mutant), selection.deadline, selection.tests)
                flakyKiller(outcome, mutant, selection.deadline, confirmation)
                    ?.let { MutantResult(mutant, MutantStatus.ERROR, "flaky test $it", coveredBy = covering) }
                    ?: outcome.toResult(mutant, covering)
            }
        } catch (e: TestExecutionStopped) {
            throw e
        } catch (e: Exception) {
            val detail = cause(e.message?.takeIf(String::isNotBlank) ?: e.toString())
            MutantResult(mutant, MutantStatus.ERROR, detail, coveredBy = covering)
        } catch (e: LinkageError) {
            // The message of a LinkageError is often just a class name.
            MutantResult(mutant, MutantStatus.ERROR, cause(e.toString()), coveredBy = covering)
        }
    }

    // A kill counts only when the killing test class passes on the original classes: one that fails there too is
    // flaky, and its failure says nothing about the mutant. Each class is confirmed once per run, on its first kill,
    // which catches a test that fails alone or every time, not every intermittent one.
    // Returns the killing test when its class is flaky.
    private fun flakyKiller(
        outcome: Outcome,
        mutant: Mutant,
        deadline: Duration,
        confirmation: Confirmation,
    ): String? {
        val killed = outcome as? Outcome.Killed
        val test = killed?.test ?: return null
        val flaky =
            confirmation.verdict(test) {
                failsOnOriginal(mutant, confirmation.originals.getValue(mutant.className), deadline, test)
            }
        return killed.by.takeIf { flaky }
    }

    // A rerun that cannot run says nothing about the test, so the kill stands; an executor that cannot continue still
    // stops the analysis.
    @Suppress("TooGenericExceptionCaught")
    private fun failsOnOriginal(
        mutant: Mutant,
        original: ByteArray,
        deadline: Duration,
        test: TestId,
    ): Boolean =
        try {
            executor.run(mutant, original, deadline, listOf(test)) is Outcome.Killed
        } catch (e: TestExecutionStopped) {
            throw e
        } catch (_: Exception) {
            false
        }
}

// One run's original class bytes and, per killing test class, whether it failed on them. The lock covers only the
// lookup; the lazy reruns a class once even when several workers ask for it at the same time.
private class Confirmation(
    val originals: Map<String, ByteArray>,
) {
    private val flaky = mutableMapOf<TestId, Lazy<Boolean>>()

    fun verdict(
        test: TestId,
        rerun: () -> Boolean,
    ): Boolean = synchronized(flaky) { flaky.getOrPut(test) { lazy(rerun) } }.value
}

private const val SHUTDOWN_WAIT_SECONDS = 5L

private const val MAX_CAUSE = 200

private const val STDERR_MARKER = ". Last stderr:"

// The baseline runs the whole suite on a cold JVM (class loading, engine start-up) that mutants then reuse.
private const val BASELINE_TIMEOUT_FACTOR = 5

private const val BASELINE_FAILED = "The tests fail on the original classes, so no mutant was scored"

private const val COVERAGE_LOST = "running every mutant against the whole suite"

// Workers must not outlive run(); the interrupt flag is restored so the caller still sees it.
private fun ExecutorService.stopAndAwait() {
    shutdownNow()
    try {
        awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun <T> Future<T>.getOrRethrowCause(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

// Stack traces stay out of the report: the first line names the cause.
private fun cause(text: String): String? =
    text
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        // The stderr tail of a crashed child is long and may hold paths; the exit state before it is the cause.
        ?.substringBefore(STDERR_MARKER)
        ?.trim()
        ?.take(MAX_CAUSE)

// Why a baseline with this outcome is not green, or null when it is.
private fun problem(
    outcome: Outcome,
    deadline: Duration,
): String? =
    when (outcome) {
        is Outcome.Killed -> "the first failure is ${outcome.by}${outcome.cause?.let { ": $it" }.orEmpty()}"
        is Outcome.LoadError -> "they cannot run: ${outcome.message}"
        Outcome.Timeout -> "they did not finish within $deadline; raise timeoutMs"
        Outcome.Survived, Outcome.NoTests -> null
    }

private fun Outcome.toResult(
    mutant: Mutant,
    covering: List<String>?,
): MutantResult =
    when (this) {
        is Outcome.Killed -> MutantResult(mutant, MutantStatus.KILLED, killedBy = by, coveredBy = covering)
        Outcome.Survived -> MutantResult(mutant, MutantStatus.SURVIVED, coveredBy = covering)
        Outcome.Timeout -> MutantResult(mutant, MutantStatus.TIMEOUT, coveredBy = covering)
        Outcome.NoTests -> MutantResult(mutant, MutantStatus.NO_COVERAGE, coveredBy = emptyList())
        is Outcome.LoadError -> MutantResult(mutant, MutantStatus.ERROR, cause(message), coveredBy = covering)
    }
