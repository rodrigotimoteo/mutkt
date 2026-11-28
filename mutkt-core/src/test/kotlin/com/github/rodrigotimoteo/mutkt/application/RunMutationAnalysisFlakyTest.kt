package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val WAIT_SECONDS = 10L

private val KILLER = TestId("a.FooTest")

private val KILLED = Outcome.Killed("a.FooTest.adds", "boom", KILLER)

class RunMutationAnalysisFlakyTest {
    private val m = mutant()

    private fun analysis(
        executor: FakeExecutor,
        mutants: List<Mutant> = listOf(m),
        parallelism: Int = 1,
    ) = RunMutationAnalysis(
        FakeClassSource(listOf(classFile(m.className))),
        FakeGenerator(mutants),
        FakeInstrumenter(),
        executor,
        RecordingWriter(),
        AnalysisConfig(ClassFilters(), 7.seconds, parallelism),
    )

    private fun run(executor: FakeExecutor): MutantResult = analysis(executor).run().results.single()

    private fun originalRuns(executor: FakeExecutor) = executor.received.count { it.second == m.className }

    @Test
    fun `reports a kill as an error when the killing test also fails on the original classes`() =
        assertEquals(
            MutantResult(m, MutantStatus.ERROR, "flaky test a.FooTest.adds"),
            run(FakeExecutor(behavior = { KILLED }, original = { KILLED })),
        )

    @Test
    fun `keeps a kill when the killing test passes on the original classes`() =
        assertEquals(
            MutantResult(m, MutantStatus.KILLED, killedBy = "a.FooTest.adds"),
            run(FakeExecutor(behavior = { KILLED }, original = { Outcome.Survived })),
        )

    @Test
    fun `reruns only the killing test class on the original bytes within the mutant deadline`() {
        val executor = FakeExecutor(behavior = { KILLED })
        run(executor)
        assertEquals(
            listOf(Triple(m, m.id.toString(), 7.seconds), Triple(m, m.className, 7.seconds)),
            executor.received.toList(),
        )
        assertEquals(listOf(null, listOf(KILLER)), executor.selections.map { it.second })
    }

    @Test
    fun `does not rerun a kill that names no test class`() {
        val executor = FakeExecutor(behavior = { Outcome.Killed("JVM exit 7") }, original = { KILLED })
        assertEquals(MutantStatus.KILLED, run(executor).status)
        assertEquals(1, executor.received.size)
    }

    @Test
    fun `confirms a trusted killing test class once and keeps its later kills`() {
        val mutants = (0..2).map { mutant(ordinal = it) }
        val executor = FakeExecutor(behavior = { KILLED })
        val results = analysis(executor, mutants).run().results
        assertEquals(List(3) { MutantStatus.KILLED }, results.map { it.status })
        assertEquals(1, originalRuns(executor))
    }

    @Test
    fun `confirms a flaky killing test class once and reports its later kills as errors`() {
        val mutants = (0..2).map { mutant(ordinal = it) }
        val byOrdinal = { mutant: Mutant -> Outcome.Killed("a.FooTest.t${mutant.id.ordinal}", null, KILLER) }
        val executor = FakeExecutor(behavior = byOrdinal, original = { KILLED })
        val results = analysis(executor, mutants).run().results
        assertEquals(List(3) { MutantStatus.ERROR }, results.map { it.status })
        assertEquals((0..2).map { "flaky test a.FooTest.t$it" }, results.map { it.detail })
        assertEquals(1, originalRuns(executor))
    }

    @Test
    fun `confirms again in a new run`() {
        val executor = FakeExecutor(behavior = { KILLED })
        val analysis = analysis(executor)
        analysis.run()
        analysis.run()
        assertEquals(2, originalRuns(executor))
    }

    @Test
    fun `keeps a kill when the rerun times out, cannot load or runs no test`() =
        listOf(Outcome.Timeout, Outcome.LoadError("x"), Outcome.NoTests).forEach { rerun ->
            assertEquals(MutantStatus.KILLED, run(FakeExecutor(behavior = { KILLED }, original = { rerun })).status)
        }

    @Test
    fun `a rerun that exits its JVM is flaky`() =
        assertEquals(
            MutantStatus.ERROR,
            run(FakeExecutor(behavior = { KILLED }, original = { Outcome.Killed("JVM exit 7") })).status,
        )

    @Test
    fun `keeps a kill when the rerun throws`() =
        assertEquals(
            MutantResult(m, MutantStatus.KILLED, killedBy = "a.FooTest.adds"),
            run(FakeExecutor(behavior = { KILLED }, original = { throw IllegalStateException("child died") })),
        )

    @Test
    fun `workers that kill with the same class at the same time rerun it once`() {
        val workers = 4
        val together = CountDownLatch(workers)
        val behavior = { _: Mutant ->
            together.countDown()
            together.await(WAIT_SECONDS, TimeUnit.SECONDS)
            KILLED
        }
        val executor = FakeExecutor(behavior = behavior)
        val mutants = (0 until workers).map { mutant(ordinal = it) }
        val results = analysis(executor, mutants, workers).run().results
        assertEquals(List(workers) { MutantStatus.KILLED }, results.map { it.status })
        assertEquals(1, originalRuns(executor))
    }
}
