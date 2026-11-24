package com.github.rodrigotimoteo.mutkt.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MutantIdTest {
    private val base = MutantId(OperatorId("negate"), "a.Foo", "run", "(I)V", 0)

    @Test
    fun `equal components give equal ids`() = assertEquals(base, base.copy())

    @Test
    fun `overloads with different descriptors never collide`() =
        assertNotEquals(base, base.copy(methodDescriptor = "(J)V"))

    @Test
    fun `ordinal differentiates sites in one method`() = assertNotEquals(base, base.copy(ordinal = 1))

    @Test
    fun `string form is stable and includes the descriptor`() = assertEquals("negate:a.Foo#run(I)V:0", base.toString())

    @Test
    fun `negative ordinal is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { base.copy(ordinal = -1) }
    }

    @Test
    fun `blank operator id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { OperatorId(" ") }
    }
}
