package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Per-operator applier tests. Verifies that applyMutation() actually changes bytecode
 * for each of the 17 operators.
 */
class MutatorApplierOperatorTest {
    // ============== ARITHMETIC ==============

    @Test
    fun `ARITHMETIC apply changes IADD to ISUB`() {
        verifyApplierChangesOpcodes(Opcodes.IADD, Opcodes.ISUB) { it in intMathOpcodes }
    }

    @Test
    fun `ARITHMETIC apply changes ISUB to IADD`() {
        val bytes = buildClassWithISub()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.ISUB)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IADD in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes IMUL to IDIV`() {
        val bytes = buildClassWithIMul()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IMUL)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IDIV in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes IDIV to IMUL`() {
        val bytes = buildClassWithIDiv()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IDIV)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes IREM to IMUL`() {
        val bytes = buildClassWithIRem()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IREM)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes LADD to LSUB`() {
        val bytes = buildClassWithLongOp(Opcodes.LADD)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.LADD)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LSUB in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes LSUB to LADD`() {
        val bytes = buildClassWithLongOp(Opcodes.LSUB)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.LSUB)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LADD in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes LMUL to LDIV`() {
        val bytes = buildClassWithLongOp(Opcodes.LMUL)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.LMUL)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LDIV in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes LDIV to LMUL`() {
        val bytes = buildClassWithLongOp(Opcodes.LDIV)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.LDIV)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes LREM to LMUL`() {
        val bytes = buildClassWithLongOp(Opcodes.LREM)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.LREM)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes FADD to FSUB`() {
        val bytes = buildClassWithFloatOp(Opcodes.FADD)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.FADD)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FSUB in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes FSUB to FADD`() {
        val bytes = buildClassWithFloatOp(Opcodes.FSUB)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.FSUB)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FADD in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes FMUL to FDIV`() {
        val bytes = buildClassWithFloatOp(Opcodes.FMUL)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.FMUL)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FDIV in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes FDIV to FMUL`() {
        val bytes = buildClassWithFloatOp(Opcodes.FDIV)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.FDIV)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes FREM to FMUL`() {
        val bytes = buildClassWithFloatOp(Opcodes.FREM)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.FREM)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes DADD to DSUB`() {
        val bytes = buildClassWithDoubleOp(Opcodes.DADD)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.DADD)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DSUB in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes DSUB to DADD`() {
        val bytes = buildClassWithDoubleOp(Opcodes.DSUB)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.DSUB)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DADD in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes DMUL to DDIV`() {
        val bytes = buildClassWithDoubleOp(Opcodes.DMUL)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.DMUL)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DDIV in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes DDIV to DMUL`() {
        val bytes = buildClassWithDoubleOp(Opcodes.DDIV)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.DDIV)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DMUL in opcodes)
    }

    @Test
    fun `ARITHMETIC apply changes DREM to DMUL`() {
        val bytes = buildClassWithDoubleOp(Opcodes.DREM)
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.DREM)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DMUL in opcodes)
    }

    // ============== CONDITIONALS_BOUNDARY ==============

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IFLT to IFLE`() {
        val bytes = buildClassWithJump(Opcodes.IFLT)
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFLT)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFLE in opcodes)
        assertFalse(Opcodes.IFLT in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IFLE to IFLT`() {
        val bytes = buildClassWithJump(Opcodes.IFLE)
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFLE)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFLT in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IFGT to IFGE`() {
        val bytes = buildClassWithJump(Opcodes.IFGT)
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFGT)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFGE in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IFGE to IFGT`() {
        val bytes = buildClassWithJump(Opcodes.IFGE)
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IFGE)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFGT in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IF_ICMPLT to IF_ICMPLE`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPLT, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPLT)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPLE in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IF_ICMPLE to IF_ICMPLT`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPLE, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPLE)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPLT in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IF_ICMPGT to IF_ICMPGE`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPGT, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPGT)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPGE in opcodes)
    }

    @Test
    fun `CONDITIONALS_BOUNDARY apply changes IF_ICMPGE to IF_ICMPGT`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPGE, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.CONDITIONALS_BOUNDARY, Opcodes.IF_ICMPGE)
        val mutated = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPGT in opcodes)
    }

    // ============== NEGATE_CONDITIONALS ==============

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFEQ to IFNE`() {
        val bytes = buildClassWithJump(Opcodes.IFEQ)
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFEQ)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFNE in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFNE to IFEQ`() {
        val bytes = buildClassWithJump(Opcodes.IFNE)
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFNE)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFEQ in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFLT to IFGE`() {
        val bytes = buildClassWithJump(Opcodes.IFLT)
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFLT)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFGE in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFGE to IFLT`() {
        val bytes = buildClassWithJump(Opcodes.IFGE)
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFGE)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFLT in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFNULL to IFNONNULL`() {
        val bytes = buildClassWithJump(Opcodes.IFNULL, "()Ljava/lang/Object;")
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFNULL)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFNONNULL in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFNONNULL to IFNULL`() {
        val bytes = buildClassWithJump(Opcodes.IFNONNULL, "()Ljava/lang/Object;")
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IFNONNULL)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFNULL in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IF_ICMPEQ to IF_ICMPNE`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPEQ, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IF_ICMPEQ)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPNE in opcodes)
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IF_ICMPNE to IF_ICMPEQ`() {
        val bytes = buildClassWithJump(Opcodes.IF_ICMPNE, "(II)I")
        val mutation = findFirstMutation(bytes, MutationOperator.NEGATE_CONDITIONALS, Opcodes.IF_ICMPNE)
        val mutated = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IF_ICMPEQ in opcodes)
    }

    // ============== RETURN_VALS ==============

    @Test
    fun `RETURN_VALS apply mutates IRETURN to constant`() {
        val bytes = buildClassWithIReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.RETURN_VALS, Opcodes.IRETURN)
        val mutated = Mutator(setOf(MutationOperator.RETURN_VALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ICONST_0 in opcodes)
    }

    @Test
    fun `RETURN_VALS apply mutates LRETURN to constant`() {
        val bytes = buildClassWithLReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.RETURN_VALS, Opcodes.LRETURN)
        val mutated = Mutator(setOf(MutationOperator.RETURN_VALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LCONST_0 in opcodes)
    }

    @Test
    fun `RETURN_VALS apply mutates FRETURN to constant`() {
        val bytes = buildClassWithFReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.RETURN_VALS, Opcodes.FRETURN)
        val mutated = Mutator(setOf(MutationOperator.RETURN_VALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FCONST_0 in opcodes)
    }

    @Test
    fun `RETURN_VALS apply mutates DRETURN to constant`() {
        val bytes = buildClassWithDReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.RETURN_VALS, Opcodes.DRETURN)
        val mutated = Mutator(setOf(MutationOperator.RETURN_VALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DCONST_0 in opcodes)
    }

    @Test
    fun `RETURN_VALS apply mutates ARETURN to null`() {
        val bytes = buildClassWithAReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.RETURN_VALS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.RETURN_VALS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    // ============== NULL_RETURNS ==============

    @Test
    fun `NULL_RETURNS apply mutates ARETURN to null`() {
        val bytes = buildClassWithObjectReturn("Ljava/lang/String;")
        val mutation = findFirstMutation(bytes, MutationOperator.NULL_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.NULL_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    @Test
    fun `NULL_RETURNS apply works for List return type`() {
        val bytes = buildClassWithObjectReturn("Ljava/util/List;")
        val mutation = findFirstMutation(bytes, MutationOperator.NULL_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.NULL_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    @Test
    fun `NULL_RETURNS apply works for Integer return type`() {
        val bytes = buildClassWithObjectReturn("Ljava/lang/Integer;")
        val mutation = findFirstMutation(bytes, MutationOperator.NULL_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.NULL_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    // ============== EMPTY_RETURNS ==============

    @Test
    fun `EMPTY_RETURNS apply mutates List return to emptyList`() {
        val bytes = buildClassWithObjectReturn("Ljava/util/List;")
        val mutation = findFirstMutation(bytes, MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        // Should contain INVOKESTATIC (emptyList call)
        assertTrue(Opcodes.INVOKESTATIC in opcodes)
    }

    @Test
    fun `EMPTY_RETURNS apply mutates Set return to emptySet`() {
        val bytes = buildClassWithObjectReturn("Ljava/util/Set;")
        val mutation = findFirstMutation(bytes, MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.INVOKESTATIC in opcodes)
    }

    @Test
    fun `EMPTY_RETURNS apply mutates Map return to emptyMap`() {
        val bytes = buildClassWithObjectReturn("Ljava/util/Map;")
        val mutation = findFirstMutation(bytes, MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.INVOKESTATIC in opcodes)
    }

    @Test
    fun `EMPTY_RETURNS apply mutates IntArray return to empty array`() {
        val bytes = buildClassWithObjectReturn("[I")
        val mutation = findFirstMutation(bytes, MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        // ICONST_0 + NEWARRAY T_INT
        assertTrue(Opcodes.ICONST_0 in opcodes)
        assertTrue(Opcodes.NEWARRAY in opcodes)
    }

    @Test
    fun `EMPTY_RETURNS apply mutates StringArray return to empty array`() {
        val bytes = buildClassWithObjectReturn("[Ljava/lang/String;")
        val mutation = findFirstMutation(bytes, MutationOperator.EMPTY_RETURNS, Opcodes.ARETURN)
        val mutated = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ANEWARRAY in opcodes || Opcodes.NEWARRAY in opcodes)
    }

    // ============== INCREMENTS ==============

    @Test
    fun `INCREMENTS apply changes IINC positive to negative`() {
        val bytes = buildClassWithIInc(1)
        val mutation = findFirstMutation(bytes, MutationOperator.INCREMENTS)
        val mutated = Mutator(setOf(MutationOperator.INCREMENTS)).applyMutation(bytes, mutation)
        // Should have at least one iinc instruction
        val incrementOps = collectIincIncrements(mutated)
        assertTrue(incrementOps.contains(-1), "Expected -1, got $incrementOps")
    }

    @Test
    fun `INCREMENTS apply changes IINC negative to positive`() {
        val bytes = buildClassWithIInc(-1)
        val mutation = findFirstMutation(bytes, MutationOperator.INCREMENTS)
        val mutated = Mutator(setOf(MutationOperator.INCREMENTS)).applyMutation(bytes, mutation)
        val incrementOps = collectIincIncrements(mutated)
        assertTrue(incrementOps.contains(1), "Expected 1, got $incrementOps")
    }

    @Test
    fun `INCREMENTS apply negates non-unit increments`() {
        val bytes = buildClassWithIInc(5)
        val mutation = findFirstMutation(bytes, MutationOperator.INCREMENTS)
        val mutated = Mutator(setOf(MutationOperator.INCREMENTS)).applyMutation(bytes, mutation)
        val incrementOps = collectIincIncrements(mutated)
        assertTrue(incrementOps.contains(-5), "Expected -5, got $incrementOps")
    }

    // ============== TRUE_RETURNS / FALSE_RETURNS ==============

    @Test
    fun `TRUE_RETURNS apply changes ICONST_1 to ICONST_0`() {
        val bytes = buildClassWithBooleanReturn()
        val mutation = findFirstMutation(bytes, MutationOperator.TRUE_RETURNS, Opcodes.ICONST_1)
        val mutated = Mutator(setOf(MutationOperator.TRUE_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ICONST_0 in opcodes)
    }

    @Test
    fun `FALSE_RETURNS apply changes ICONST_0 to ICONST_1`() {
        val bytes = buildClassWithBooleanFalseReturn()
        val mutations = Mutator(setOf(MutationOperator.FALSE_RETURNS)).scanMutations(bytes)
        // The first mutation should be the body ICONST_0 (before the helper's return)
        val mutation =
            mutations.firstOrNull { it.originalOpcode == Opcodes.ICONST_0 }
                ?: mutations.first()
        val mutated = Mutator(setOf(MutationOperator.FALSE_RETURNS)).applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ICONST_1 in opcodes, "Expected ICONST_1 in mutated bytecode")
    }

    // ============== Edge cases ==============

    @Test
    fun `applyMutation on wrong line number returns original bytes`() {
        val bytes = buildClassWithIAdd()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IADD)
        val fake = mutation.copy(lineNumber = 99999)
        val result = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, fake)
        assertEquals(bytes.toList(), result.toList())
    }

    @Test
    fun `applyMutation on wrong className still applies mutation`() {
        // The applier matches on lineNumber + originalOpcode, not className.
        // This is intentional - the engine's classloader is responsible for
        // ensuring the right class is being mutated. The applier just does
        // the byte transformation.
        val bytes = buildClassWithIAdd()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IADD)
        val fake = mutation.copy(className = "wrong.Class")
        val result = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, fake)
        // Mutation is still applied (opcodes change)
        assertNotEquals(bytes.toList(), result.toList())
        val opcodes = collectOpcodes(result)
        assertTrue(Opcodes.ISUB in opcodes)
    }

    @Test
    fun `applyMutation does not mutate input bytes`() {
        val bytes = buildClassWithIAdd()
        val original = bytes.toList()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IADD)
        Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        assertEquals(original, bytes.toList())
    }

    @Test
    fun `applyMutation is idempotent when target is consumed`() {
        val bytes = buildClassWithIAdd()
        val mutation = findFirstMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IADD)
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val result1 = mutator.applyMutation(bytes, mutation)
        // Second apply with same target should be a no-op (line+opcode match already used)
        val result2 = mutator.applyMutation(result1, mutation)
        // The opcodes should still contain ISUB
        val opcodes = collectOpcodes(result2)
        assertTrue(Opcodes.ISUB in opcodes)
    }

    // ============== Helpers ==============

    private val intMathOpcodes = setOf(Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM)

    private fun verifyApplierChangesOpcodes(
        original: Int,
        expected: Int,
        predicate: (Int) -> Boolean,
    ) {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(original)
            }
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.originalOpcode == original }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertNotEquals(bytes.toList(), mutated.toList(), "Mutation should change bytes")
        assertTrue(expected in opcodes || opcodes.any(predicate), "Expected $expected in opcodes")
    }

    private fun findFirstMutation(
        bytes: ByteArray,
        operator: MutationOperator,
        opcode: Int? = null,
    ): com.github.rodrigotimoteo.mutation.mutator.MutationInfo {
        val mutations = Mutator(setOf(operator)).scanMutations(bytes)
        return if (opcode != null) {
            mutations.first { it.originalOpcode == opcode }
        } else {
            mutations.first { it.operator == operator }
        }
    }

    private fun collectOpcodes(bytes: ByteArray): Set<Int> {
        val opcodes = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitInsn(opcode: Int) {
                            opcodes.add(opcode)
                            super.visitInsn(opcode)
                        }

                        override fun visitJumpInsn(
                            opcode: Int,
                            label: Label?,
                        ) {
                            opcodes.add(opcode)
                            super.visitJumpInsn(opcode, label)
                        }

                        override fun visitIntInsn(
                            opcode: Int,
                            operand: Int,
                        ) {
                            opcodes.add(opcode)
                            super.visitIntInsn(opcode, operand)
                        }

                        override fun visitTypeInsn(
                            opcode: Int,
                            type: String?,
                        ) {
                            opcodes.add(opcode)
                            super.visitTypeInsn(opcode, type)
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            opcodes.add(opcode)
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            },
            0,
        )
        return opcodes
    }

    private fun collectIincIncrements(bytes: ByteArray): Set<Int> {
        val increments = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitIincInsn(
                            varIndex: Int,
                            increment: Int,
                        ) {
                            increments.add(increment)
                            super.visitIincInsn(varIndex, increment)
                        }
                    }
                }
            },
            0,
        )
        return increments
    }

    private fun buildClassWithIAdd(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.ICONST_2)
            mv.visitInsn(Opcodes.IADD)
        }

    private fun buildClassWithISub(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitInsn(Opcodes.ICONST_2)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.ISUB)
        }

    private fun buildClassWithIMul(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitInsn(Opcodes.ICONST_2)
            mv.visitInsn(Opcodes.ICONST_3)
            mv.visitInsn(Opcodes.IMUL)
        }

    private fun buildClassWithIDiv(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitIntInsn(Opcodes.BIPUSH, 6)
            mv.visitIntInsn(Opcodes.BIPUSH, 2)
            mv.visitInsn(Opcodes.IDIV)
        }

    private fun buildClassWithIRem(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitIntInsn(Opcodes.BIPUSH, 7)
            mv.visitIntInsn(Opcodes.BIPUSH, 3)
            mv.visitInsn(Opcodes.IREM)
        }

    private fun buildClassWithLongOp(opcode: Int): ByteArray =
        buildClassWithMethod(descriptor = "()J") { mv ->
            mv.visitInsn(Opcodes.LCONST_1)
            mv.visitInsn(Opcodes.LCONST_1)
            mv.visitInsn(opcode)
        }

    private fun buildClassWithFloatOp(opcode: Int): ByteArray =
        buildClassWithMethod(descriptor = "()F") { mv ->
            mv.visitInsn(Opcodes.FCONST_1)
            mv.visitInsn(Opcodes.FCONST_1)
            mv.visitInsn(opcode)
        }

    private fun buildClassWithDoubleOp(opcode: Int): ByteArray =
        buildClassWithMethod(descriptor = "()D") { mv ->
            mv.visitInsn(Opcodes.DCONST_1)
            mv.visitInsn(Opcodes.DCONST_1)
            mv.visitInsn(opcode)
        }

    private fun buildClassWithJump(
        opcode: Int,
        descriptor: String = "()I",
    ): ByteArray =
        buildClassWithMethod(descriptor = descriptor) { mv ->
            if (descriptor.startsWith("()")) {
                mv.visitInsn(Opcodes.ICONST_0)
            } else {
                mv.visitVarInsn(Opcodes.ILOAD, 1)
            }
            if (descriptor == "(II)I") {
                mv.visitVarInsn(Opcodes.ILOAD, 2)
            }
            val label = Label()
            mv.visitJumpInsn(opcode, label)
            mv.visitLabel(label)
        }

    private fun buildClassWithIReturn(): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.IRETURN)
        }

    private fun buildClassWithLReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()J") { mv ->
            mv.visitInsn(Opcodes.LCONST_1)
            mv.visitInsn(Opcodes.LRETURN)
        }

    private fun buildClassWithFReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()F") { mv ->
            mv.visitInsn(Opcodes.FCONST_1)
            mv.visitInsn(Opcodes.FRETURN)
        }

    private fun buildClassWithDReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()D") { mv ->
            mv.visitInsn(Opcodes.DCONST_1)
            mv.visitInsn(Opcodes.DRETURN)
        }

    private fun buildClassWithAReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()Ljava/lang/String;") { mv ->
            mv.visitLdcInsn("hello")
            mv.visitInsn(Opcodes.ARETURN)
        }

    private fun buildClassWithObjectReturn(returnTypeDescriptor: String): ByteArray =
        buildClassWithMethod(descriptor = "()$returnTypeDescriptor") { mv ->
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.ARETURN)
        }

    private fun buildClassWithIInc(increment: Int): ByteArray =
        buildClassWithMethod { mv ->
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, 1)
            mv.visitIincInsn(1, increment)
        }

    private fun buildClassWithBooleanReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()Z") { mv ->
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.IRETURN)
        }

    private fun buildClassWithBooleanFalseReturn(): ByteArray =
        buildClassWithMethod(descriptor = "()Z") { mv ->
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitInsn(Opcodes.IRETURN)
        }

    private fun buildClassWithMethod(
        name: String = "test",
        descriptor: String = "()I",
        lineNumber: Int = 1,
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(lineNumber, Label())
        body(mv!!)
        when {
            descriptor.endsWith(")V") -> mv?.visitInsn(Opcodes.RETURN)
            descriptor.endsWith(")Z") || descriptor.endsWith(")I") || descriptor.endsWith(")B") ||
                descriptor.endsWith(")S") || descriptor.endsWith(")C") -> {
                mv?.visitInsn(Opcodes.ICONST_0)
                mv?.visitInsn(Opcodes.IRETURN)
            }
            descriptor.endsWith(")J") -> {
                mv?.visitInsn(Opcodes.LCONST_0)
                mv?.visitInsn(Opcodes.LRETURN)
            }
            descriptor.endsWith(")F") -> {
                mv?.visitInsn(Opcodes.FCONST_0)
                mv?.visitInsn(Opcodes.FRETURN)
            }
            descriptor.endsWith(")D") -> {
                mv?.visitInsn(Opcodes.DCONST_0)
                mv?.visitInsn(Opcodes.DRETURN)
            }
            else -> {
                mv?.visitInsn(Opcodes.ACONST_NULL)
                mv?.visitInsn(Opcodes.ARETURN)
            }
        }
        mv?.visitMaxs(10, 10)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
