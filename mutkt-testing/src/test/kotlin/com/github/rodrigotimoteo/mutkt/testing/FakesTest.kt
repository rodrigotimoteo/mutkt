package com.github.rodrigotimoteo.mutkt.testing

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class FakesTest {
    private val operator = OperatorId("negate")

    private fun mutant(ordinal: Int) =
        Mutant(MutantId(operator, "a.Foo", "run", "()V", ordinal), operator, "a.Foo", "run", "()V", 3, "negated")

    private val first = mutant(0)
    private val second = mutant(1)

    @Test
    fun `class source returns its classes`() {
        val source = FakeClassSource(listOf(ClassFile("a.Foo", byteArrayOf(1)), ClassFile("a.Bar", byteArrayOf(2))))

        val read = source.classes()

        assertEquals(listOf("a.Foo", "a.Bar"), read.map { it.name })
        assertArrayEquals(byteArrayOf(2), read[1].bytes)
    }

    @Test
    fun `generator discovers its mutants and materializes registered bytes`() {
        val generator = FakeMutantGenerator(listOf(first, second), mapOf(first to byteArrayOf(7)))

        assertEquals(listOf(first, second), generator.discover(emptyList(), emptyList()))
        assertArrayEquals(byteArrayOf(7), generator.materialize(first))
        assertThrows(IllegalStateException::class.java) { generator.materialize(second) }
    }

    @Test
    fun `executor returns scripted outcomes and records runs`() {
        val executor = FakeTestExecutor(mapOf(first to Outcome.Killed("FooTest")))

        assertEquals(Outcome.Killed("FooTest"), executor.run(first, byteArrayOf(1), 5.seconds))
        assertEquals(Outcome.Survived, executor.run(second, byteArrayOf(2), 1.seconds, listOf(TestId("BTest"))))
        assertEquals(listOf(first, second), executor.runs.map { it.first })
        assertArrayEquals(byteArrayOf(1), executor.runs[0].second)
        assertArrayEquals(byteArrayOf(2), executor.runs[1].second)
        assertEquals(listOf(5.seconds, 1.seconds), executor.runs.map { it.third })
        assertEquals(listOf(null, listOf(TestId("BTest"))), executor.selections)
    }

    @Test
    fun `executor answers the baseline with its scripted outcome and records the deadline`() {
        val executor = FakeTestExecutor(baseline = Outcome.Killed("FooTest"), baselineElapsed = 2.seconds)

        assertEquals(Baseline(Outcome.Killed("FooTest"), 2.seconds), executor.baseline(3.seconds))
        assertEquals(listOf(3.seconds), executor.baselines)
        assertEquals(emptyList<Any>(), executor.runs)
    }

    @Test
    fun `executor answers an instrumented baseline with its own outcome and the coverage`() {
        val coverage = Coverage(emptyList(), emptySet())
        val probed = Instrumented(emptyList(), emptyMap())
        val executor =
            FakeTestExecutor(
                baselineElapsed = 2.seconds,
                coverage = coverage,
                instrumentedBaseline = Outcome.LoadError("VerifyError"),
            )

        val answer = executor.baseline(3.seconds, probed)

        assertEquals(Baseline(Outcome.LoadError("VerifyError"), 2.seconds, coverage), answer)
        assertEquals(Baseline(Outcome.Survived, 2.seconds), executor.baseline(4.seconds))
        assertEquals(listOf(probed, null), executor.instrumented)
    }

    @Test
    fun `report writer records reports`() {
        val writer = FakeReportWriter()
        val report = MutationReport(listOf(MutantResult(first, MutantStatus.KILLED)))

        writer.write(report)

        assertEquals(listOf(report), writer.reports)
    }

    @Test
    fun `result history reads what was stored, then what was last written`() {
        val stored = History("f0", emptyList())
        val next = History("f1", listOf(HistoryEntry(first.id.toString(), "k", MutantStatus.SURVIVED)))
        val history = FakeResultHistory(stored)

        assertEquals(stored, history.read())
        history.write(next)

        assertEquals(next, history.read())
        assertEquals(listOf(next), history.written)
        assertNull(FakeResultHistory().read())
    }

    @Test
    fun `source lookup finds known lines only`() {
        val lookup = FakeSourceLookup(mapOf(("a.Foo" to 3) to "if (x) y()"))

        assertEquals("if (x) y()", lookup.lineText("a.Foo", "Foo.kt", 3))
        assertNull(lookup.lineText("a.Foo", "Foo.kt", 4))
    }

    @Test
    fun `source lookup joins the lines of a class and leaves gaps empty`() {
        val lookup = FakeSourceLookup(mapOf(("a.Foo" to 1) to "a", ("a.Foo" to 3) to "c", ("b.Bar" to 1) to "x"))

        assertEquals("a\n\nc", lookup.sourceText("a.Foo", "Foo.kt"))
        assertNull(lookup.sourceText("a.Baz", "Baz.kt"))
    }
}
