package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MutationDescriberTest {
    private fun info(
        operator: MutationOperator,
        originalOpcode: Int = 0,
        mutatedOpcode: Int = 0,
        description: String = "Test mutation",
        className: String = "com.Foo",
        methodName: String = "testMethod",
    ) = MutationInfo(
        operator = operator,
        className = className,
        methodName = methodName,
        methodDescriptor = "()V",
        lineNumber = 10,
        description = description,
        originalOpcode = originalOpcode,
        mutatedOpcode = mutatedOpcode,
    )

    @Test
    fun `describe CONDITIONALS_BOUNDARY mentions boundary`() {
        val desc =
            MutationDescriber.describe(
                info(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPGE),
            )
        assertContains(desc, "Boundary")
    }

    @Test
    fun `describe NEGATE_CONDITIONALS mentions negate`() {
        val desc =
            MutationDescriber.describe(
                info(MutationOperator.NEGATE_CONDITIONALS, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE),
            )
        assertContains(desc, "Negate")
    }

    @Test
    fun `describe ARITHMETIC mentions arithmetic`() {
        val desc =
            MutationDescriber.describe(
                info(MutationOperator.ARITHMETIC, Opcodes.IADD, Opcodes.ISUB),
            )
        assertContains(desc, "Arithmetic")
    }

    @Test
    fun `describe RETURN_VALS mentions return value`() {
        val desc =
            MutationDescriber.describe(
                info(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_0),
            )
        assertContains(desc, "Return")
    }

    @Test
    fun `describe NULL_RETURNS returns null message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.NULL_RETURNS))
        assertEquals("Return null", desc)
    }

    @Test
    fun `describe EMPTY_RETURNS returns empty collection message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.EMPTY_RETURNS))
        assertContains(desc, "empty")
    }

    @Test
    fun `describe INVERT_NEGS returns invert negation`() {
        val desc = MutationDescriber.describe(info(MutationOperator.INVERT_NEGS))
        assertEquals("Invert negation", desc)
    }

    @Test
    fun `describe TRUE_RETURNS returns boolean message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.TRUE_RETURNS))
        assertContains(desc, "true")
    }

    @Test
    fun `describe FALSE_RETURNS returns boolean message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.FALSE_RETURNS))
        assertContains(desc, "false")
    }

    @Test
    fun `describe VOID_METHOD_CALLS mentions void call`() {
        val desc = MutationDescriber.describe(info(MutationOperator.VOID_METHOD_CALLS))
        assertContains(desc, "void")
    }

    @Test
    fun `describe CONSTRUCTOR_CALLS mentions constructor`() {
        val desc = MutationDescriber.describe(info(MutationOperator.CONSTRUCTOR_CALLS))
        assertContains(desc, "constructor")
    }

    @Test
    fun `describe NON_VOID_METHOD_CALLS mentions non-void`() {
        val desc = MutationDescriber.describe(info(MutationOperator.NON_VOID_METHOD_CALLS))
        assertContains(desc, "non-void")
    }

    @Test
    fun `describe INCREMENTS mentions increment`() {
        val desc = MutationDescriber.describe(info(MutationOperator.INCREMENTS))
        assertContains(desc, "Increment")
    }

    @Test
    fun `describe DATA_CLASS_COPY returns data class message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.DATA_CLASS_COPY))
        assertContains(desc, "Data class")
    }

    @Test
    fun `describe SEALED_WHEN returns sealed when message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.SEALED_WHEN))
        assertContains(desc, "Sealed")
    }

    @Test
    fun `describe NULL_SAFETY returns null safety message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.NULL_SAFETY))
        assertContains(desc, "Null-safety")
    }

    @Test
    fun `describe COROUTINE returns coroutine message`() {
        val desc = MutationDescriber.describe(info(MutationOperator.COROUTINE))
        assertContains(desc, "Coroutine")
    }

    @Test
    fun `describe non-null result for all operators`() {
        for (operator in MutationOperator.values()) {
            val desc = MutationDescriber.describe(info(operator))
            assertNotNull(desc)
        }
    }
}
