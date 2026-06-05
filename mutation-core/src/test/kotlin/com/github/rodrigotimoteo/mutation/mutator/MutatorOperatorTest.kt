package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutatorOperatorTest {
    @Test
    fun `MVP_OPERATORS contains 7 expected operators`() {
        val mvp = MutationOperator.MVP_OPERATORS
        assertEquals(7, mvp.size)
        assertTrue(MutationOperator.CONDITIONALS_BOUNDARY in mvp)
        assertTrue(MutationOperator.NEGATE_CONDITIONALS in mvp)
        assertTrue(MutationOperator.ARITHMETIC in mvp)
        assertTrue(MutationOperator.RETURN_VALS in mvp)
        assertTrue(MutationOperator.NULL_RETURNS in mvp)
        assertTrue(MutationOperator.EMPTY_RETURNS in mvp)
        assertTrue(MutationOperator.INVERT_NEGS in mvp)
    }

    @Test
    fun `QUICK_WIN_OPERATORS contains expected operators`() {
        val qw = MutationOperator.QUICK_WIN_OPERATORS
        assertTrue(MutationOperator.VOID_METHOD_CALLS in qw)
        assertTrue(MutationOperator.INCREMENTS in qw)
        assertTrue(MutationOperator.TRUE_RETURNS in qw)
        assertTrue(MutationOperator.FALSE_RETURNS in qw)
        assertTrue(MutationOperator.CONSTRUCTOR_CALLS in qw)
        assertTrue(MutationOperator.NON_VOID_METHOD_CALLS in qw)
    }

    @Test
    fun `KOTLIN_OPERATORS contains 4 expected operators`() {
        val k = MutationOperator.KOTLIN_OPERATORS
        assertEquals(4, k.size)
        assertTrue(MutationOperator.DATA_CLASS_COPY in k)
        assertTrue(MutationOperator.SEALED_WHEN in k)
        assertTrue(MutationOperator.NULL_SAFETY in k)
        assertTrue(MutationOperator.COROUTINE in k)
    }

    @Test
    fun `fromName returns correct operator for valid names`() {
        assertEquals(MutationOperator.ARITHMETIC, MutationOperator.fromName("ARITHMETIC"))
        assertEquals(MutationOperator.COROUTINE, MutationOperator.fromName("COROUTINE"))
        assertEquals(MutationOperator.RETURN_VALS, MutationOperator.fromName("RETURN_VALS"))
    }

    @Test
    fun `fromName returns null for invalid names`() {
        assertNull(MutationOperator.fromName("DOES_NOT_EXIST"))
        assertNull(MutationOperator.fromName(""))
        assertNull(MutationOperator.fromName("arithmetic"))
    }

    @Test
    fun `all operators have unique operatorName`() {
        val names = MutationOperator.values().map { it.operatorName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `operatorName matches enum name for all operators`() {
        MutationOperator.values().forEach { op ->
            assertEquals(op.name, op.operatorName)
        }
    }
}
