package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationDescriberOperatorTest {

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IFEQ and IFNE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFEQ, Opcodes.IFNE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: == 0 -> != 0", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IFLT and IFLE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFLT, Opcodes.IFLE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: < 0 -> <= 0", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IFGT and IFGE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFGT, Opcodes.IFGE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: > 0 -> >= 0", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IF_ICMPLT and IF_ICMPLE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPLE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: < -> <=", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IF_ICMPGT and IF_ICMPGE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPGE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: > -> >=", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IF_ICMPEQ and IF_ICMPNE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: == -> !=", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with IF_ACMPEQ and IF_ACMPNE`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: == -> !=", result)
    }

    @Test
    fun `describe NEGATE_CONDITIONALS with IFEQ and IFNE`() {
        val m = makeMutation(MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFEQ, Opcodes.IFNE)
        val result = MutationDescriber.describe(m)
        assertEquals("Negate: == 0 -> != 0", result)
    }

    @Test
    fun `describe NEGATE_CONDITIONALS with IFLT and IFGE`() {
        val m = makeMutation(MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFLT, Opcodes.IFGE)
        val result = MutationDescriber.describe(m)
        assertEquals("Negate: < 0 -> >= 0", result)
    }

    @Test
    fun `describe NEGATE_CONDITIONALS with IFGT and IFLE`() {
        val m = makeMutation(MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFGT, Opcodes.IFLE)
        val result = MutationDescriber.describe(m)
        assertEquals("Negate: > 0 -> <= 0", result)
    }

    @Test
    fun `describe ARITHMETIC with IADD and ISUB`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.IADD, Opcodes.ISUB)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> -", result)
    }

    @Test
    fun `describe ARITHMETIC with IMUL and IDIV`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.IMUL, Opcodes.IDIV)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: * -> /", result)
    }

    @Test
    fun `describe ARITHMETIC with IREM`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.IADD, Opcodes.IREM)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> %", result)
    }

    @Test
    fun `describe ARITHMETIC with LADD and LSUB`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.LADD, Opcodes.LSUB)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> -", result)
    }

    @Test
    fun `describe ARITHMETIC with LMUL and LDIV`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.LMUL, Opcodes.LDIV)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: * -> /", result)
    }

    @Test
    fun `describe ARITHMETIC with LREM`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.LADD, Opcodes.LREM)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> %", result)
    }

    @Test
    fun `describe ARITHMETIC with FADD and FSUB`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.FADD, Opcodes.FSUB)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> -", result)
    }

    @Test
    fun `describe ARITHMETIC with FMUL and FDIV`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.FMUL, Opcodes.FDIV)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: * -> /", result)
    }

    @Test
    fun `describe ARITHMETIC with FREM`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.FADD, Opcodes.FREM)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> %", result)
    }

    @Test
    fun `describe ARITHMETIC with DADD and DSUB`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.DADD, Opcodes.DSUB)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> -", result)
    }

    @Test
    fun `describe ARITHMETIC with DMUL and DDIV`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.DMUL, Opcodes.DDIV)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: * -> /", result)
    }

    @Test
    fun `describe ARITHMETIC with DREM`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, Opcodes.DADD, Opcodes.DREM)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: + -> %", result)
    }

    @Test
    fun `describe ARITHMETIC with unknown opcode`() {
        val m = makeMutation(MutationOperator.ARITHMETIC, 9999, 9998)
        val result = MutationDescriber.describe(m)
        assertEquals("Arithmetic: ?9999 -> ?9998", result)
    }

    @Test
    fun `describe CONDITIONALS_BOUNDARY with unknown opcode`() {
        val m = makeMutation(MutationOperator.CONDITIONALS_BOUNDARY, 9999, 9998)
        val result = MutationDescriber.describe(m)
        assertEquals("Boundary: ?9999 -> ?9998", result)
    }

    @Test
    fun `describe RETURN_VALS ICONST_0`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_0)
        assertEquals("Return 0", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_1`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_1)
        assertEquals("Return 1", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_M1`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_M1)
        assertEquals("Return -1", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_2`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_2)
        assertEquals("Return 2", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_3`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_3)
        assertEquals("Return 3", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_4`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_4)
        assertEquals("Return 4", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS ICONST_5`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, Opcodes.ICONST_5)
        assertEquals("Return 5", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS LCONST_0`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.LRETURN, Opcodes.LCONST_0)
        assertEquals("Return 0L", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS LCONST_1`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.LRETURN, Opcodes.LCONST_1)
        assertEquals("Return 1L", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS FCONST_0`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.FRETURN, Opcodes.FCONST_0)
        assertEquals("Return 0.0f", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS FCONST_1`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.FRETURN, Opcodes.FCONST_1)
        assertEquals("Return 1.0f", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS FCONST_2`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.FRETURN, Opcodes.FCONST_2)
        assertEquals("Return 2.0f", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS DCONST_0`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.DRETURN, Opcodes.DCONST_0)
        assertEquals("Return 0.0", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS DCONST_1`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.DRETURN, Opcodes.DCONST_1)
        assertEquals("Return 1.0", MutationDescriber.describe(m))
    }

    @Test
    fun `describe RETURN_VALS with unknown mutated opcode`() {
        val m = makeMutation(MutationOperator.RETURN_VALS, Opcodes.IRETURN, 9999)
        assertEquals("Return constant", MutationDescriber.describe(m))
    }

    @Test
    fun `describe NULL_RETURNS returns fixed string`() {
        val m = makeMutation(MutationOperator.NULL_RETURNS, Opcodes.ARETURN, Opcodes.ACONST_NULL)
        assertEquals("Return null", MutationDescriber.describe(m))
    }

    @Test
    fun `describe EMPTY_RETURNS returns fixed string`() {
        val m = makeMutation(MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN, Opcodes.ACONST_NULL)
        assertEquals("Return empty collection/array", MutationDescriber.describe(m))
    }

    @Test
    fun `describe INVERT_NEGS returns fixed string`() {
        val m = makeMutation(MutationOperator.INVERT_NEGS, Opcodes.INEG, Opcodes.INEG)
        assertEquals("Invert negation", MutationDescriber.describe(m))
    }

    @Test
    fun `describe TRUE_RETURNS returns fixed string`() {
        val m = makeMutation(MutationOperator.TRUE_RETURNS, Opcodes.IRETURN, Opcodes.ICONST_0)
        assertEquals("Boolean return: true -> false", MutationDescriber.describe(m))
    }

    @Test
    fun `describe FALSE_RETURNS returns fixed string`() {
        val m = makeMutation(MutationOperator.FALSE_RETURNS, Opcodes.IRETURN, Opcodes.ICONST_1)
        assertEquals("Boolean return: false -> true", MutationDescriber.describe(m))
    }

    @Test
    fun `describe INCREMENTS includes opcodes`() {
        val m = makeMutation(MutationOperator.INCREMENTS, 132, -132)
        val result = MutationDescriber.describe(m)
        assertTrue(result.startsWith("Increment:"))
        assertTrue(result.contains("132"))
        assertTrue(result.contains("-132"))
    }

    @Test
    fun `describe VOID_METHOD_CALLS extracts from description`() {
        val m = MutationInfo(
            operator = MutationOperator.VOID_METHOD_CALLS,
            className = "com.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = 10,
            description = "Remove void call: java/io/PrintStream.println",
            originalOpcode = 182,
            mutatedOpcode = 182,
        )
        val result = MutationDescriber.describe(m)
        assertEquals("Remove void call: java/io/PrintStream.println", result)
    }

    @Test
    fun `describe VOID_METHOD_CALLS with multiple colons takes last segment`() {
        val m = MutationInfo(
            operator = MutationOperator.VOID_METHOD_CALLS,
            className = "com.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = 10,
            description = "pre: middle: java/io/PrintStream.println",
            originalOpcode = 182,
            mutatedOpcode = 182,
        )
        val result = MutationDescriber.describe(m)
        assertEquals("Remove void call: java/io/PrintStream.println", result)
    }

    @Test
    fun `describe CONSTRUCTOR_CALLS extracts from description`() {
        val m = MutationInfo(
            operator = MutationOperator.CONSTRUCTOR_CALLS,
            className = "com.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = 10,
            description = "Remove constructor: java/lang/String.<init>",
            originalOpcode = 183,
            mutatedOpcode = 183,
        )
        val result = MutationDescriber.describe(m)
        assertEquals("Remove constructor: java/lang/String.<init>", result)
    }

    @Test
    fun `describe NON_VOID_METHOD_CALLS extracts from description`() {
        val m = MutationInfo(
            operator = MutationOperator.NON_VOID_METHOD_CALLS,
            className = "com.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = 10,
            description = "Remove non-void call: java/lang/String.length",
            originalOpcode = 182,
            mutatedOpcode = 182,
        )
        val result = MutationDescriber.describe(m)
        assertEquals("Remove non-void call: java/lang/String.length", result)
    }

    @Test
    fun `describe DATA_CLASS_COPY returns fixed string`() {
        val m = makeMutation(MutationOperator.DATA_CLASS_COPY, 183, 0)
        assertEquals("Data class copy mutation", MutationDescriber.describe(m))
    }

    @Test
    fun `describe SEALED_WHEN returns fixed string`() {
        val m = makeMutation(MutationOperator.SEALED_WHEN, 170, 0)
        assertEquals("Sealed class when mutation", MutationDescriber.describe(m))
    }

    @Test
    fun `describe NULL_SAFETY returns fixed string`() {
        val m = makeMutation(MutationOperator.NULL_SAFETY, 184, 0)
        assertEquals("Null-safety mutation", MutationDescriber.describe(m))
    }

    @Test
    fun `describe COROUTINE returns fixed string`() {
        val m = makeMutation(MutationOperator.COROUTINE, 184, 0)
        assertEquals("Coroutine mutation", MutationDescriber.describe(m))
    }

    private fun makeMutation(op: MutationOperator, originalOpcode: Int, mutatedOpcode: Int): MutationInfo {
        return MutationInfo(
            operator = op,
            className = "com.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = 10,
            description = "test",
            originalOpcode = originalOpcode,
            mutatedOpcode = mutatedOpcode,
        )
    }
}
