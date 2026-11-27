package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 20.seconds

class RunMutationAnalysisCoverageTest {
    private val writer = RecordingWriter()
    private val add = mutant(method = "add")
    private val sub = mutant(method = "sub")
    private val other = mutant(className = "a.Bar", method = "other")
    private val probes = mapOf(add.id to Probe("a.Foo", 0), sub.id to Probe("a.Foo", 1))

    // ATest reaches add, BTest reaches add and sub, CTest nothing; a.Bar has no probe.
    private val coverage =
        Coverage(
            listOf(
                TestCoverage(TestId("ATest"), setOf(Probe("a.Foo", 0)), 1.seconds),
                TestCoverage(TestId("BTest"), setOf(Probe("a.Foo", 0), Probe("a.Foo", 1)), 500.milliseconds),
                TestCoverage(TestId("CTest"), emptySet(), 1.seconds),
            ),
            emptySet(),
        )

    private val mutants = listOf(add, sub, other)
    private val generator = FakeGenerator(mutants)
    private val instrumenter = FakeInstrumenter(probes)

    private fun run(
        executor: FakeExecutor,
        warnings: MutableCollection<String> = mutableListOf(),
    ) = RunMutationAnalysis(
        FakeClassSource(listOf(classFile("a.Foo"), classFile("a.Bar"))),
        generator,
        instrumenter,
        executor,
        writer,
        AnalysisConfig(ClassFilters(), TIMEOUT, 2),
    ).run(onWarning = { warnings += it })

    private fun FakeExecutor.selectionOf(mutant: Mutant) = selections.single { it.first == mutant }.second

    private fun FakeExecutor.deadlineOf(mutant: Mutant) = received.single { it.first == mutant }.third

    @Test
    fun `instruments the discovered mutants and runs one baseline on the instrumented classes`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds, coverage = coverage)
        run(executor)
        assertEquals(listOf(listOf("a.Foo", "a.Bar") to mutants), instrumenter.calls.toList())
        assertSame(instrumenter.made.single(), executor.baselineInputs.single())
        assertEquals(listOf(TIMEOUT * 5), executor.baselines.toList())
    }

    @Test
    fun `each mapped mutant runs only the test classes that reach it, in baseline order`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds, coverage = coverage)
        run(executor)
        assertEquals(listOf(TestId("ATest"), TestId("BTest")), executor.selectionOf(add))
        assertEquals(listOf(TestId("BTest")), executor.selectionOf(sub))
    }

    @Test
    fun `a covered mutant's deadline comes from its test classes' times`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds, coverage = coverage)
        run(executor)
        // 4 × (their time + the longest class's 1 s as warm-up) + 4 s.
        assertEquals(14.seconds, executor.deadlineOf(add))
        assertEquals(10.seconds, executor.deadlineOf(sub))
    }

    @Test
    fun `an unmapped mutant runs the whole suite with the baseline's deadline`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds, coverage = coverage)
        run(executor)
        assertEquals(null, executor.selectionOf(other))
        assertEquals(16.seconds, executor.deadlineOf(other))
    }

    @Test
    fun `a mutant no test reaches is NO_COVERAGE without being materialized or run`() {
        val reachesAdd = coverage.copy(tests = coverage.tests.take(1))
        val events = ConcurrentLinkedQueue<MutantResult>()
        val executor =
            FakeExecutor(baselineElapsed = 3.seconds, coverage = reachesAdd) {
                if (it == add) Outcome.Killed("ATest.adds") else Outcome.Survived
            }
        val failing = FakeGenerator(mutants, failing = setOf(sub))
        val report =
            RunMutationAnalysis(
                FakeClassSource(listOf(classFile("a.Foo"), classFile("a.Bar"))),
                failing,
                instrumenter,
                executor,
                writer,
                AnalysisConfig(ClassFilters(), TIMEOUT, 2),
            ).run(progress = { events += it })
        assertEquals(MutantResult(sub, MutantStatus.NO_COVERAGE), report.results[1])
        assertTrue(executor.selections.none { it.first == sub })
        assertEquals(3, events.size)
        assertEquals(1.0 / 3, report.score)
    }

    @Test
    fun `without coverage every mutant runs the whole suite`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds)
        run(executor)
        assertEquals(listOf(null, null, null), mutants.map { executor.selectionOf(it) })
        assertEquals(listOf(16.seconds, 16.seconds, 16.seconds), mutants.map { executor.deadlineOf(it) })
    }

    @Test
    fun `a red instrumented baseline falls back to the original classes, warns and runs the whole suite`() {
        val warnings = mutableListOf<String>()
        val executor =
            FakeExecutor(
                baselineElapsed = 3.seconds,
                coverage = coverage,
                instrumentedBaseline = { Outcome.LoadError("VerifyError: probes") },
            )
        val report = run(executor, warnings)
        assertEquals(listOf(true, false), executor.baselineInputs.map { it != null })
        assertEquals(listOf(null, null, null), mutants.map { executor.selectionOf(it) })
        assertEquals(3, report.results.size)
        assertEquals(
            listOf(
                "The baseline on probed classes failed (they cannot run: VerifyError: probes); " +
                    "running every mutant against the whole suite",
            ),
            warnings,
        )
    }

    @Test
    fun `an instrumented baseline that throws falls back to the original classes`() {
        val warnings = mutableListOf<String>()
        val executor =
            FakeExecutor(
                baselineElapsed = 3.seconds,
                coverage = coverage,
                instrumentedBaseline = { throw IllegalStateException("child died") },
            )
        run(executor, warnings)
        assertEquals(2, executor.baselines.size)
        assertTrue("child died" in warnings.single(), warnings.single())
    }

    @Test
    fun `a green instrumented baseline warns nothing`() {
        val warnings = mutableListOf<String>()
        run(FakeExecutor(baselineElapsed = 3.seconds, coverage = coverage), warnings)
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `a red baseline on both the instrumented and the original classes stops the run with the original's failure`() {
        val executor =
            FakeExecutor(
                baseline = { Outcome.Killed("a.FooTest.plain") },
                instrumentedBaseline = { Outcome.Killed("a.FooTest.probed") },
            )
        val error = assertThrows(IllegalStateException::class.java) { run(executor) }
        assertTrue(
            "no mutant was scored: the first failure is a.FooTest.plain" in error.message.orEmpty(),
            error.message,
        )
        assertTrue(executor.received.isEmpty())
        assertTrue(writer.written.isEmpty())
    }
}
