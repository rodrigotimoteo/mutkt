package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertTrue

class MutatorScannerOperatorTest {
    // === ARITHMETIC (20 tests) ===

    @Test
    fun `ARITHMETIC scans IADD`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.IADD })
    }

    @Test
    fun `ARITHMETIC scans ISUB`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ISUB)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.ISUB })
    }

    @Test
    fun `ARITHMETIC scans IMUL`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_3)
                mv.visitInsn(Opcodes.ICONST_4)
                mv.visitInsn(Opcodes.IMUL)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.IMUL })
    }

    @Test
    fun `ARITHMETIC scans IDIV`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_4)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IDIV)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.IDIV })
    }

    @Test
    fun `ARITHMETIC scans IREM`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_5)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IREM)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.IREM })
    }

    @Test
    fun `ARITHMETIC scans LADD`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LCONST_1)
                mv.visitInsn(Opcodes.LADD)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.LADD })
    }

    @Test
    fun `ARITHMETIC scans LSUB`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_1)
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LSUB)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.LSUB })
    }

    @Test
    fun `ARITHMETIC scans LMUL`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_1)
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LMUL)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.LMUL })
    }

    @Test
    fun `ARITHMETIC scans LDIV`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_1)
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LDIV)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.LDIV })
    }

    @Test
    fun `ARITHMETIC scans LREM`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_1)
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LREM)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.LREM })
    }

    @Test
    fun `ARITHMETIC scans FADD`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_0)
                mv.visitInsn(Opcodes.FCONST_1)
                mv.visitInsn(Opcodes.FADD)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.FADD })
    }

    @Test
    fun `ARITHMETIC scans FSUB`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_1)
                mv.visitInsn(Opcodes.FCONST_0)
                mv.visitInsn(Opcodes.FSUB)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.FSUB })
    }

    @Test
    fun `ARITHMETIC scans FMUL`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_1)
                mv.visitInsn(Opcodes.FCONST_2)
                mv.visitInsn(Opcodes.FMUL)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.FMUL })
    }

    @Test
    fun `ARITHMETIC scans FDIV`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_2)
                mv.visitInsn(Opcodes.FCONST_1)
                mv.visitInsn(Opcodes.FDIV)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.FDIV })
    }

    @Test
    fun `ARITHMETIC scans FREM`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_2)
                mv.visitInsn(Opcodes.FCONST_1)
                mv.visitInsn(Opcodes.FREM)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.FREM })
    }

    @Test
    fun `ARITHMETIC scans DADD`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DCONST_1)
                mv.visitInsn(Opcodes.DADD)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.DADD })
    }

    @Test
    fun `ARITHMETIC scans DSUB`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_1)
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DSUB)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.DSUB })
    }

    @Test
    fun `ARITHMETIC scans DMUL`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_1)
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DMUL)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.DMUL })
    }

    @Test
    fun `ARITHMETIC scans DDIV`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_1)
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DDIV)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.DDIV })
    }

    @Test
    fun `ARITHMETIC scans DREM`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_1)
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DREM)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.DREM })
    }

    // === CONDITIONALS_BOUNDARY (8 tests) ===

    @Test
    fun `CONDITIONALS_BOUNDARY scans IFLT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFLT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IFLT })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IFLE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFLE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IFLE })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IFGT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_3)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFGT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IFGT })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IFGE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_4)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFGE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IFGE })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IF_ICMPLT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPLT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IF_ICMPLT })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IF_ICMPLE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_3)
                mv.visitInsn(Opcodes.ICONST_4)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPLE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IF_ICMPLE })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IF_ICMPGT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_5)
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPGT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IF_ICMPGT })
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IF_ICMPGE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.ICONST_2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPGE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY && it.originalOpcode == Opcodes.IF_ICMPGE })
    }

    // === NEGATE_CONDITIONALS (10 tests) ===

    @Test
    fun `NEGATE_CONDITIONALS scans IFEQ`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFEQ })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFNE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFNE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFNE })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFLT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFLT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFLT })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFGE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFGE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFGE })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFGT`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_3)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFGT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFGT })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFLE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_4)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFLE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFLE })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IF_ICMPEQ`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IF_ICMPEQ })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IF_ICMPNE`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IF_ICMPNE })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFNULL`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFNULL })
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFNONNULL`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFNONNULL, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NEGATE_CONDITIONALS && it.originalOpcode == Opcodes.IFNONNULL })
    }

    // === RETURN_VALS (4 tests) ===

    @Test
    fun `RETURN_VALS scans IRETURN`() {
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.RETURN_VALS && it.originalOpcode == Opcodes.IRETURN })
    }

    @Test
    fun `RETURN_VALS scans LRETURN`() {
        val bytes =
            buildClassWithMethod(descriptor = "()J") { mv ->
                mv.visitInsn(Opcodes.LCONST_0)
                mv.visitInsn(Opcodes.LRETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.RETURN_VALS && it.originalOpcode == Opcodes.LRETURN })
    }

    @Test
    fun `RETURN_VALS scans FRETURN`() {
        val bytes =
            buildClassWithMethod(descriptor = "()F") { mv ->
                mv.visitInsn(Opcodes.FCONST_0)
                mv.visitInsn(Opcodes.FRETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.RETURN_VALS && it.originalOpcode == Opcodes.FRETURN })
    }

    @Test
    fun `RETURN_VALS scans DRETURN`() {
        val bytes =
            buildClassWithMethod(descriptor = "()D") { mv ->
                mv.visitInsn(Opcodes.DCONST_0)
                mv.visitInsn(Opcodes.DRETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.RETURN_VALS && it.originalOpcode == Opcodes.DRETURN })
    }

    // === NULL_RETURNS (4 tests with different return types) ===

    @Test
    fun `NULL_RETURNS scans ARETURN returning String`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/lang/String;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NULL_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `NULL_RETURNS scans ARETURN returning Object`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/lang/Object;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NULL_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `NULL_RETURNS scans ARETURN returning Date`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/util/Date;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NULL_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `NULL_RETURNS scans ARETURN returning Integer`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/lang/Integer;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.NULL_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    // === EMPTY_RETURNS (5 tests) ===

    @Test
    fun `EMPTY_RETURNS scans ARETURN returning List`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/util/List;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.EMPTY_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `EMPTY_RETURNS scans ARETURN returning Set`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/util/Set;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.EMPTY_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `EMPTY_RETURNS scans ARETURN returning Map`() {
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/util/Map;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.EMPTY_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `EMPTY_RETURNS scans ARETURN returning IntArray`() {
        val bytes =
            buildClassWithMethod(descriptor = "()[I") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.EMPTY_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    @Test
    fun `EMPTY_RETURNS scans ARETURN returning StringArray`() {
        val bytes =
            buildClassWithMethod(descriptor = "()[Ljava/lang/String;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.EMPTY_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.EMPTY_RETURNS && it.originalOpcode == Opcodes.ARETURN })
    }

    // === INCREMENTS (2 tests) ===

    @Test
    fun `INCREMENTS scans IINC positive`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 1)
            }
        val mutations = Mutator(setOf(MutationOperator.INCREMENTS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.INCREMENTS && it.originalOpcode == Opcodes.IINC })
    }

    @Test
    fun `INCREMENTS scans IINC negative`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, -1)
            }
        val mutations = Mutator(setOf(MutationOperator.INCREMENTS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.INCREMENTS && it.originalOpcode == Opcodes.IINC })
    }

    // === Builder ===

    private fun buildClassWithMethod(
        name: String = "test",
        descriptor: String = "()I",
        lineNumber: Int = 1,
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
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
