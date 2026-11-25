package com.github.rodrigotimoteo.mutkt.testing

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
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

        assertEquals(listOf(first, second), generator.discover(emptyList()))
        assertArrayEquals(byteArrayOf(7), generator.materialize(first))
        assertThrows(IllegalStateException::class.java) { generator.materialize(second) }
    }

    @Test
    fun `executor returns scripted outcomes and records runs`() {
        val executor = FakeTestExecutor(mapOf(first to Outcome.Killed("FooTest")))

        assertEquals(Outcome.Killed("FooTest"), executor.run(first, byteArrayOf(1), 5.seconds))
        assertEquals(Outcome.Survived, executor.run(second, byteArrayOf(2), 1.seconds))
        assertEquals(listOf(first, second), executor.runs.map { it.first })
        assertArrayEquals(byteArrayOf(1), executor.runs[0].second)
        assertArrayEquals(byteArrayOf(2), executor.runs[1].second)
        assertEquals(listOf(5.seconds, 1.seconds), executor.runs.map { it.third })
    }

    @Test
    fun `executor answers the baseline with its scripted outcome and records the deadline`() {
        val executor = FakeTestExecutor(baseline = Outcome.Killed("FooTest"))

        assertEquals(Outcome.Killed("FooTest"), executor.baseline(3.seconds))
        assertEquals(listOf(3.seconds), executor.baselines)
        assertEquals(emptyList<Any>(), executor.runs)
    }

    @Test
    fun `report writer records reports`() {
        val writer = FakeReportWriter()
        val report = MutationReport(listOf(MutantResult(first, MutantStatus.KILLED)))

        writer.write(report)

        assertEquals(listOf(report), writer.reports)
    }

    @Test
    fun `source lookup finds known lines only`() {
        val lookup = FakeSourceLookup(mapOf(("a.Foo" to 3) to "if (x) y()"))

        assertEquals("if (x) y()", lookup.lineText("a.Foo", "Foo.kt", 3))
        assertNull(lookup.lineText("a.Foo", "Foo.kt", 4))
    }
}
