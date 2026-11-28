package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.KILLED
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.NO_COVERAGE
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.SURVIVED
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MethodSurvivorsTest {
    private fun result(
        status: MutantStatus,
        method: String,
        line: Int?,
        coveredBy: List<String>?,
        className: String = "a.Foo",
    ): MutantResult {
        // "name(I)V" names an overload; a bare name gets ()V.
        val name = method.substringBefore('(')
        val descriptor = if ('(' in method) "(" + method.substringAfter('(') else "()V"
        val op = OperatorId("arithmetic")
        val id = MutantId(op, className, name, descriptor, 0)
        return MutantResult(Mutant(id, op, className, name, descriptor, line, "d"), status, coveredBy = coveredBy)
    }

    @Test
    fun `only survivors are grouped, the method with the most first, ties by class then method`() {
        val groups =
            survivorsByMethod(
                listOf(
                    result(SURVIVED, "one", 1, listOf("T")),
                    result(SURVIVED, "two", 9, listOf("T")),
                    result(SURVIVED, "two", 3, listOf("T")),
                    result(SURVIVED, "one", 1, listOf("T"), className = "a.Bar"),
                    result(SURVIVED, "one(I)V", 1, listOf("T")),
                    result(KILLED, "zero", 1, listOf("T")),
                    result(KILLED, "zero", 1, listOf("T")),
                    result(NO_COVERAGE, "zero", 1, emptyList()),
                ),
            )

        assertEquals(listOf("a.Foo.two()V", "a.Bar.one()V", "a.Foo.one()V", "a.Foo.one(I)V"), groups.map { it.method })
        assertEquals(listOf(3, 9), groups.first().survivors.map { it.mutant.line })
    }

    @Test
    fun `covering test classes are joined in first-seen order without repeats`() {
        val group =
            survivorsByMethod(
                listOf(
                    result(SURVIVED, "m", 2, listOf("B", "A")),
                    result(SURVIVED, "m", 1, listOf("A", "C")),
                ),
            ).single()

        assertEquals(listOf("B", "A", "C"), group.coveredBy)
    }

    @Test
    fun `a survivor that ran the whole suite makes the method covered by the whole suite`() {
        val group =
            survivorsByMethod(
                listOf(result(SURVIVED, "m", 1, listOf("A")), result(SURVIVED, "m", null, null)),
            ).single()

        assertNull(group.coveredBy)
        assertEquals(listOf(1, null), group.survivors.map { it.mutant.line })
    }
}
