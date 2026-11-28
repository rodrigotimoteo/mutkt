package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurvivorBaselineTest {
    private val accepted = Survivor("boundary:a.Gate#check(I)Z:0", "m1")
    private val baseline = SurvivorBaseline(setOf(accepted))

    @Test
    fun `accepts a survivor whose id and method code both match`() =
        assertEquals(emptyList<Survivor>(), baseline.unaccepted(listOf(accepted)))

    @Test
    fun `flags a survivor at an accepted id once its method code changed`() {
        // A new site earlier in the method takes ordinal 0, so the id alone would wrongly match.
        val moved = listOf(Survivor(accepted.id, "m2"), Survivor("boundary:a.Gate#check(I)Z:1", "m2"))

        assertEquals(moved, baseline.unaccepted(moved))
        assertTrue(baseline.accepted(accepted.id))
        assertFalse(baseline.accepted("boundary:a.Gate#check(I)Z:1"))
    }

    @Test
    fun `flags a survivor of another method`() {
        val other = Survivor("boundary:a.Gate#other(I)Z:0", "m1")

        assertEquals(listOf(other), baseline.unaccepted(listOf(other, accepted)))
    }

    @Test
    fun `counts SURVIVED and NO_COVERAGE mutants as survivors, keyed with their method digest`() {
        val report = MutationReport(MutantStatus.entries.mapIndexed { i, status -> MutantResult(mutant(i), status) })

        val survivors = report.survivors { "${it.methodName}${it.methodDescriptor}" }

        assertEquals(
            listOf(Survivor("${mutant(1).id}", "run()I"), Survivor("${mutant(3).id}", "run()I")),
            survivors,
        )
    }

    private fun mutant(ordinal: Int): Mutant {
        val op = OperatorId("negate")
        return Mutant(MutantId(op, "a.Foo", "run", "()I", ordinal), op, "a.Foo", "run", "()I", 1, "negated")
    }
}
