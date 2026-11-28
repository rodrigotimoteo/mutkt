package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

// foo is covered by ATest (extends BaseTest, reaches a.Foo), bar by BTest (reaches a.Bar and a.Baz), whole has no
// probe so it runs the whole suite; nothing reaches a.Qux.
class RunMutationAnalysisHistoryTest {
    private val foo = mutant(className = "a.Foo", method = "add")
    private val bar = mutant(className = "a.Bar", method = "run")
    private val whole = mutant(className = "a.Baz", method = "baz")
    private val all = listOf(foo, bar, whole)
    private var mutants = all
    private val probes = mapOf(foo.id to Probe("a.Foo", 0), bar.id to Probe("a.Bar", 0))
    private val coverage =
        Coverage(
            listOf(
                TestCoverage(TestId("ATest"), setOf(Probe("a.Foo", 0)), 1.seconds, setOf("a.Foo")),
                TestCoverage(TestId("BTest"), setOf(Probe("a.Bar", 0)), 1.seconds, setOf("a.Bar", "a.Baz")),
            ),
            emptySet(),
        )
    private val main = listOf("a.Foo", "a.Bar", "a.Baz", "a.Qux").map(::classFile)
    private val tests = listOf("ATest", "ATest\$Nested", "BaseTest", "BTest").map(::classFile)
    private val supertypes =
        mapOf(
            "ATest" to setOf("BaseTest", "java.lang.Object"),
            "BTest" to setOf("java.lang.Object"),
        )
    private val fingerprint =
        RunFingerprint(
            "0.5.0",
            emptySet(),
            emptySet(),
            emptySet(),
            emptySet(),
            20.seconds,
            2,
            emptyList(),
            "21",
            "/p",
            emptyMap(),
            emptyMap(),
        )
    private val history = MemoryHistory()
    private var unprobed = emptySet<String>()

    private fun behavior(mutant: Mutant): Outcome =
        when (mutant) {
            foo -> Outcome.Killed("ATest.adds", KillKind.ASSERTION)
            bar -> Outcome.Timeout
            else -> Outcome.Survived
        }

    private class Run(
        val report: MutationReport,
        val executor: FakeExecutor,
    ) {
        val ran get() = executor.selections.map { it.first }.toSet()
    }

    private fun run(
        main: List<ClassFile> = this.main,
        tests: List<ClassFile> = this.tests,
        // Null runs without a history.
        fingerprint: RunFingerprint? = this.fingerprint,
        coverage: Coverage = this.coverage,
        behavior: (Mutant) -> Outcome = ::behavior,
    ): Run {
        val executor = FakeExecutor(baselineElapsed = 1.seconds, coverage = coverage, behavior = behavior)
        val report =
            RunMutationAnalysis(
                FakeClassSource(main),
                FakeGenerator(mutants),
                FakeInstrumenter(probes, unprobed),
                executor,
                RecordingWriter(),
                AnalysisConfig(ClassFilters(), 20.seconds, 2),
                fingerprint?.let { HistoryReuse(history, it, FakeClassSource(tests), supertypes) },
            ).run()
        return Run(report, executor)
    }

    private fun changed(
        classes: List<ClassFile>,
        name: String,
    ) = classes.map { if (it.name == name) ClassFile(name, byteArrayOf(9)) else it }

    @Test
    fun `an unchanged second run executes only the baseline and reuses every result`() {
        val first = run()
        val second = run()

        assertEquals(emptySet<Mutant>(), second.ran)
        assertEquals(1, second.executor.baselines.size)
        assertEquals(first.report.results.map { it.copy(reused = true) }, second.report.results)
        assertEquals(3, second.report.reused)
        assertEquals(0, first.report.reused)
    }

    @Test
    fun `a reused killed result keeps who killed it and how`() {
        run()

        assertEquals(
            MutantResult(
                foo,
                MutantStatus.KILLED,
                killedBy = "ATest.adds",
                coveredBy = listOf("ATest"),
                killKind = KillKind.ASSERTION,
                reused = true,
            ),
            run().report.results[0],
        )
    }

    @Test
    fun `a changed test class re-runs only the mutants it covers`() {
        run()

        assertEquals(setOf(foo, whole), run(tests = changed(tests, "ATest")).ran)
    }

    @Test
    fun `a changed nested test class counts as its outer test class`() {
        run()

        assertEquals(setOf(foo, whole), run(tests = changed(tests, "ATest\$Nested")).ran)
    }

    @Test
    fun `a changed enclosing class re-runs the mutants of its static nested test class`() {
        // A static nested root test class: ATest$Nested runs on its own, ATest only holds it.
        val first = coverage.tests.first().copy(test = TestId("ATest\$Nested"))
        val nested = coverage.copy(tests = listOf(first) + coverage.tests.drop(1))
        run(coverage = nested)

        assertEquals(setOf(foo, whole), run(tests = changed(tests, "ATest"), coverage = nested).ran)
    }

    @Test
    fun `a changed main class re-runs only the mutants whose tests reach it`() {
        run()
        val bazChanged = changed(main, "a.Baz")

        assertEquals(setOf(bar, whole), run(main = bazChanged).ran)
        assertEquals(setOf(whole), run(main = changed(bazChanged, "a.Qux")).ran)
    }

    @Test
    fun `an unprobed class counts as reached by every test`() {
        unprobed = setOf("a.Qux")
        run()

        assertEquals(setOf(foo, bar, whole), run(main = changed(main, "a.Qux")).ran)
    }

    @Test
    fun `a class reached outside any test class counts as reached by every test`() {
        val qux = coverage.copy(globalReach = setOf("a.Qux"))
        run(coverage = qux)

        assertEquals(setOf(foo, bar, whole), run(main = changed(main, "a.Qux"), coverage = qux).ran)
    }

    @Test
    fun `a changed test superclass in the test sources re-runs the mutants its subclasses cover`() {
        run()

        assertEquals(setOf(foo, whole), run(tests = changed(tests, "BaseTest")).ran)
    }

    @Test
    fun `a narrower run keeps the stored results of the mutants it did not discover`() {
        run()
        mutants = listOf(foo)
        run(tests = changed(tests, "ATest"))
        mutants = all

        assertEquals(
            listOf(foo, bar, whole).map { it.id.toString() },
            history.written
                .last()
                .entries
                .map { it.mutant },
        )
        // whole keys on every test class, so ATest's change since the first run reaches it.
        assertEquals(setOf(whole), run(tests = changed(tests, "ATest")).ran)
    }

    @Test
    fun `a narrower run under another fingerprint keeps nothing it did not run`() {
        run()
        mutants = listOf(foo)
        run(fingerprint = fingerprint.copy(java = "17"))

        assertEquals(
            listOf(foo.id.toString()),
            history.written
                .last()
                .entries
                .map { it.mutant },
        )
    }

    @Test
    fun `a changed fingerprint re-runs everything`() {
        run()

        assertEquals(all.toSet(), run(fingerprint = fingerprint.copy(java = "17")).ran)
    }

    @Test
    fun `errors and mutants without coverage are recomputed and never stored`() {
        val barUncovered = coverage.copy(tests = coverage.tests.take(1))
        val loadError: (Mutant) -> Outcome = { if (it == foo) Outcome.LoadError("VerifyError") else Outcome.Survived }
        val first = run(coverage = barUncovered, behavior = loadError)

        val statuses = first.report.results.map { it.status }
        assertEquals(listOf(MutantStatus.ERROR, MutantStatus.NO_COVERAGE, MutantStatus.SURVIVED), statuses)
        assertEquals(
            listOf(whole.id.toString()),
            history.written
                .single()
                .entries
                .map(HistoryEntry::mutant),
        )
        assertEquals(setOf(foo, bar), run().ran)
    }

    @Test
    fun `each run stores its fresh and reused results under the current fingerprint`() {
        run()
        run(tests = changed(tests, "BTest"))

        val last = history.written.last()
        assertEquals(fingerprint.digest(), last.fingerprint)
        assertEquals(
            listOf(MutantStatus.KILLED to "ATest.adds", MutantStatus.TIMEOUT to null, MutantStatus.SURVIVED to null),
            last.entries.map { it.status to it.killedBy },
        )
    }

    @Test
    fun `without a history every mutant runs and nothing is stored`() {
        run(fingerprint = null)

        assertEquals(all.toSet(), run(fingerprint = null).ran)
        assertTrue(history.written.isEmpty())
    }
}
