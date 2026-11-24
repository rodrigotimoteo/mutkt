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
        val classes = classSource.classes().filter { filters.acceptsClass(it.name) }
        val mutants =
            filters.limit(
                generator.discover(classes).filter { filters.acceptsMethod(it.className, it.methodName) },
            )
        return MutationReport(analyse(mutants, progress)).also(writer::write)
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
        MutantResult(
            mutant,
            try {
                executor.run(mutant, generator.materialize(mutant), config.timeout).toStatus()
            } catch (_: Exception) {
                MutantStatus.ERROR
            } catch (_: LinkageError) {
                MutantStatus.ERROR
            },
        )
}

private const val SHUTDOWN_WAIT_SECONDS = 5L

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

private fun Outcome.toStatus(): MutantStatus =
    when (this) {
        is Outcome.Killed -> MutantStatus.KILLED
        Outcome.Survived -> MutantStatus.SURVIVED
        Outcome.Timeout -> MutantStatus.TIMEOUT
        Outcome.NoTests -> MutantStatus.NO_COVERAGE
        is Outcome.LoadError -> MutantStatus.ERROR
    }
