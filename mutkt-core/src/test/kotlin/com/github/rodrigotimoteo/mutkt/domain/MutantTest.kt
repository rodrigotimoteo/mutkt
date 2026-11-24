package com.github.rodrigotimoteo.mutkt.domain

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MutantTest {
    private val operator = OperatorId("negate")
    private val id = MutantId(operator, "a.Foo", "run", "(I)V", 0)

    private fun mutant(className: String = "a.Foo") = Mutant(id, operator, className, "run", "(I)V", 3, "negated")

    @Test
    fun `matching fields are accepted`() {
        mutant()
    }

    @Test
    fun `fields that disagree with the id are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { mutant(className = "a.Bar") }
    }
}
