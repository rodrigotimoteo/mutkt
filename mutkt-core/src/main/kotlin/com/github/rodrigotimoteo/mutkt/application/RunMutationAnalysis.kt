package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
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

/** Discovers mutants, runs the tests against each one and writes the report. Holds no state between runs. */
public class RunMutationAnalysis(
    private val classSource: ClassSource,
    private val generator: MutantGenerator,
    private val executor: TestExecutor,
    private val writer: ReportWriter,
    private val config: AnalysisConfig,
) {
    /** Runs the analysis; [progress] is called on the calling thread once per finished mutant, in completion order. */
    public fun run(progress: (MutantResult) -> Unit = {}): MutationReport {
        val filters = config.filters
        val classes = classSource.classes()
        val targets = classes.filter { filters.acceptsClass(it.name) }
        val mutants =
            filters.limit(
                generator.discover(classes, targets).filter { filters.acceptsMethod(it.className, it.methodName) },
            )
        // Without a green suite every failing test would count as a kill, so nothing is scored.
        if (mutants.isNotEmpty()) requireGreenBaseline()
        return MutationReport(analyse(mutants, progress)).also(writer::write)
    }

    private fun requireGreenBaseline() {
        val deadline = config.timeout * BASELINE_TIMEOUT_FACTOR
        val problem =
            try {
                when (val outcome = executor.baseline(deadline)) {
                    is Outcome.Killed -> "the first failure is ${outcome.by}"
                    is Outcome.LoadError -> "they cannot run: ${outcome.message}"
                    Outcome.Timeout -> "they did not finish within $deadline; raise timeoutMs"
                    Outcome.Survived, Outcome.NoTests -> return
                }
            } catch (e: IllegalStateException) {
                throw IllegalStateException("$BASELINE_FAILED: ${e.message}", e)
            }
        error("$BASELINE_FAILED: $problem")
    }

    // Results are keyed by discovery index so completion order never leaks into the report.
    private fun analyse(
        mutants: List<Mutant>,
        progress: (MutantResult) -> Unit,
    ): List<MutantResult> {
        val pool = Executors.newFixedThreadPool(config.parallelism)
        try {
            val completion = ExecutorCompletionService<IndexedValue<MutantResult>>(pool)
            for ((index, mutant) in mutants.withIndex()) {
                completion.submit(Callable { IndexedValue(index, evaluate(mutant)) })
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

    // A mutant can make the JVM refuse its class (LinkageError), which is that mutant's ERROR. Any other Error
    // means the run itself is broken and must surface.
    @Suppress("TooGenericExceptionCaught")
    private fun evaluate(mutant: Mutant): MutantResult =
        try {
            executor.run(mutant, generator.materialize(mutant), config.timeout).toResult(mutant)
        } catch (e: Exception) {
            MutantResult(mutant, MutantStatus.ERROR, cause(e.message?.takeIf(String::isNotBlank) ?: e.toString()))
        } catch (e: LinkageError) {
            // The message of a LinkageError is often just a class name.
            MutantResult(mutant, MutantStatus.ERROR, cause(e.toString()))
        }
}

private const val SHUTDOWN_WAIT_SECONDS = 5L

private const val MAX_CAUSE = 200

private const val STDERR_MARKER = ". Last stderr:"

// The baseline runs the whole suite on a cold JVM (class loading, engine start-up) that mutants then reuse.
private const val BASELINE_TIMEOUT_FACTOR = 5

private const val BASELINE_FAILED = "The tests fail on the original classes, so no mutant was scored"

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

private fun Outcome.toResult(mutant: Mutant): MutantResult =
    when (this) {
        is Outcome.Killed -> MutantResult(mutant, MutantStatus.KILLED, killedBy = by)
        Outcome.Survived -> MutantResult(mutant, MutantStatus.SURVIVED)
        Outcome.Timeout -> MutantResult(mutant, MutantStatus.TIMEOUT)
        Outcome.NoTests -> MutantResult(mutant, MutantStatus.NO_COVERAGE)
        is Outcome.LoadError -> MutantResult(mutant, MutantStatus.ERROR, cause(message))
    }
