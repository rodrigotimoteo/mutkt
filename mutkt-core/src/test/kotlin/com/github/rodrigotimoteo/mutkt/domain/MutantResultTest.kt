package com.github.rodrigotimoteo.mutkt.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MutantResultTest {
    private val operator = OperatorId("math")
    private val mutant =
        Mutant(MutantId(operator, "a.Calc", "add", "(II)I", 0), operator, "a.Calc", "add", "(II)I", 1, "d")

    @Test
    fun `a killed mutant keeps the kind of its killing failure`() {
        val result = MutantResult(mutant, MutantStatus.KILLED, killedBy = "t", killKind = KillKind.ASSERTION)

        assertEquals(KillKind.ASSERTION, result.killKind)
    }

    @Test
    fun `only a killed mutant has a kill kind`() {
        assertThrows(IllegalArgumentException::class.java) {
            MutantResult(mutant, MutantStatus.SURVIVED, killKind = KillKind.EXCEPTION)
        }
    }
}
