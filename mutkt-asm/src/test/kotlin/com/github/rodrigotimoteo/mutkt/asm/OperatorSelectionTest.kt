package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OperatorSelectionTest {
    private val files =
        compileKotlin(
            """
            class C {
                fun f(a: Int, b: Int): Int = if (a > b) a + b else 0
                fun g(l: MutableList<Int>) { l.clear() }
            }
            """.trimIndent(),
        ).map { (name, bytes) -> ClassFile(name, bytes) }

    private fun operatorsFor(ids: Set<String>) =
        AsmMutantGenerator(ids) { null }
            .discover(files)
            .map { it.operator.value }
            .toSet()

    @Test
    fun `an empty selection enables only the default operators`() {
        assertEquals(
            setOf("negate-conditionals", "arithmetic", "conditionals-boundary", "return-vals"),
            operatorsFor(emptySet()),
        )
    }

    @Test
    fun `an empty selection leaves out the opt-in operators`() {
        val ids = operatorsFor(emptySet())

        assertTrue("null-safety" !in ids && "void-method-calls" !in ids, ids.toString())
    }

    @Test
    fun `an opt-in operator runs only when selected`() {
        assertEquals(setOf("void-method-calls"), operatorsFor(setOf("void-method-calls")))
    }

    @Test
    fun `a selection yields only the mutants of the chosen operators`() {
        assertEquals(setOf("arithmetic"), operatorsFor(setOf("arithmetic")))
        assertEquals(setOf("arithmetic", "return-vals"), operatorsFor(setOf("return-vals", "arithmetic")))
    }

    @Test
    fun `an unknown id fails with the valid ids`() {
        val error = assertThrows<IllegalArgumentException> { AsmMutantGenerator(setOf("arithmetic", "nope")) { null } }

        val message = error.message.orEmpty()
        assertTrue(message.startsWith("Unknown operator 'nope';"), message)
        assertTrue("negate-conditionals" in message && "empty-returns" in message, message)
    }
}
