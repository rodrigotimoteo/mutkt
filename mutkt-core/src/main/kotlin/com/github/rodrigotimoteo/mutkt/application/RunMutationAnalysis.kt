package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.CoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
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
 * With [history] it reuses the last run's KILLED, SURVIVED and TIMEOUT results of mutants whose key did not change, and
 * stores this run's results for the next one.
 */
@Suppress("LongParameterList")
public class RunMutationAnalysis(
    private val classSource: ClassSource,
    private val generator: MutantGenerator,
    private val instrumenter: CoverageInstrumenter,
    private val executor: TestExecutor,
    private val writer: ReportWriter,
    private val config: AnalysisConfig,
    private val history: HistoryReuse? = null,
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
        // Read first so a corrupt history fails before the baseline.
        val stored = history?.history?.read()
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
        val reuse =
            history?.let { ResultReuse(stored, it, classes, baseline.coverage, instrumented.unprobed) }
        val evaluated = analyse(mutants, map, reuse, progress)
        reuse?.let { history?.history?.write(it.next(evaluated)) }
        return MutationReport(evaluated.map { it.result }, omitted, cap).also(writer::write)
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
        reuse: ResultReuse?,
        progress: (MutantResult) -> Unit,
    ): List<Keyed> {
        val pool = Executors.newFixedThreadPool(config.parallelism)
        try {
            val completion = ExecutorCompletionService<IndexedValue<Keyed>>(pool)
            for ((index, mutant) in mutants.withIndex()) {
                completion.submit(Callable { IndexedValue(index, evaluate(mutant, map.selection(mutant), reuse)) })
            }
            val byIndex = mutableMapOf<Int, Keyed>()
            repeat(mutants.size) {
                val (index, keyed) = completion.take().getOrRethrowCause()
                byIndex[index] = keyed
                progress(keyed.result)
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
        reuse: ResultReuse?,
    ): Keyed {
        val covering = selection.tests?.map { it.className }
        var key: String? = null
        val result =
            try {
                if (covering?.isEmpty() == true) {
                    MutantResult(mutant, MutantStatus.NO_COVERAGE, coveredBy = covering)
                } else {
                    val bytes = generator.materialize(mutant)
                    key = reuse?.key(mutant, bytes, selection.tests)
                    key?.let { reuse?.reusable(mutant.id, it) }?.toResult(mutant, covering)
                        ?: executor.run(mutant, bytes, selection.deadline, selection.tests).toResult(mutant, covering)
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
        return Keyed(result, key)
    }
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

private fun HistoryEntry.toResult(
    mutant: Mutant,
    covering: List<String>?,
): MutantResult =
    MutantResult(mutant, status, killedBy = killedBy, coveredBy = covering, killKind = killKind, reused = true)

private fun Outcome.toResult(
    mutant: Mutant,
    covering: List<String>?,
): MutantResult =
    when (this) {
        is Outcome.Killed ->
            MutantResult(mutant, MutantStatus.KILLED, killedBy = by, coveredBy = covering, killKind = kind)
        Outcome.Survived -> MutantResult(mutant, MutantStatus.SURVIVED, coveredBy = covering)
        Outcome.Timeout -> MutantResult(mutant, MutantStatus.TIMEOUT, coveredBy = covering)
        Outcome.NoTests -> MutantResult(mutant, MutantStatus.NO_COVERAGE, coveredBy = emptyList())
        is Outcome.LoadError -> MutantResult(mutant, MutantStatus.ERROR, cause(message), coveredBy = covering)
    }
