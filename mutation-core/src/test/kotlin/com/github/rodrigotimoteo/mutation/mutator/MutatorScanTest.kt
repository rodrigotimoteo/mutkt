package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutatorScanTest {
    @Test
    fun `ARITHMETIC scans IADD`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.ARITHMETIC })
    }

    @Test
    fun `ARITHMETIC scans ISUB IMUL IDIV IREM`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.ISUB)
                mv.visitInsn(Opcodes.IMUL)
                mv.visitInsn(Opcodes.IDIV)
                mv.visitInsn(Opcodes.IREM)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(mutations.size >= 4, "Expected at least 4 mutations, got ${mutations.size}")
    }

    @Test
    fun `CONDITIONALS_BOUNDARY scans IFLT`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFLT, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IFEQ`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `RETURN_VALS scans IRETURN`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `NULL_RETURNS scans ARETURN on object method`() {
        val bytes =
            buildClassWithMethod("test", "()Ljava/lang/String;") { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_RETURNS)).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `INCREMENTS scans IINC`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 1)
            }
        val mutations = Mutator(setOf(MutationOperator.INCREMENTS)).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `INVERT_NEGS generates no mutations (intentional no-op)`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFNE, l)
                mv.visitLabel(l)
            }
        val mutations = Mutator(setOf(MutationOperator.INVERT_NEGS)).scanMutations(bytes)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `class with no methods generates no mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Empty", null, "java/lang/Object", null)
        cw.visitEnd()
        val mutations = Mutator(MutationOperator.MVP_OPERATORS).scanMutations(cw.toByteArray())
        assertEquals(0, mutations.size)
    }

    @Test
    fun `class with all-synthetic methods generates no mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC, "Synth", null, "java/lang/Object", null)
        cw.visitMethod(Opcodes.ACC_SYNTHETIC, "synth", "()V", null, null)?.visitEnd()
        cw.visitEnd()
        val mutations = Mutator(MutationOperator.MVP_OPERATORS).scanMutations(cw.toByteArray())
        assertEquals(0, mutations.size)
    }

    @Test
    fun `SuppressMutations annotation suppresses all mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Suppressed", null, "java/lang/Object", null)
        cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        val mutations = Mutator(MutationOperator.MVP_OPERATORS).scanMutations(cw.toByteArray())
        assertEquals(0, mutations.size)
    }

    @Test
    fun `MVP_OPERATORS scan generates multiple mutations for arithmetic method`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutations = Mutator(MutationOperator.MVP_OPERATORS).scanMutations(bytes)
        assertTrue(mutations.size >= 2, "Expected at least 2 mutations, got ${mutations.size}")
    }

    @Test
    fun `QUICK_WIN_OPERATORS scans void println call`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
        mv?.visitLdcInsn("hello")
        mv?.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val mutations = Mutator(MutationOperator.QUICK_WIN_OPERATORS).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.VOID_METHOD_CALLS })
    }

    @Test
    fun `empty enabledOperators set generates no mutations`() {
        val bytes =
            buildClassWithMethod { mv: MethodVisitor ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutations = Mutator(emptySet()).scanMutations(bytes)
        assertEquals(0, mutations.size)
    }

    private fun buildClassWithMethod(
        name: String = "test",
        descriptor: String = "()I",
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null)
        mv?.visitCode()
        body(mv!!)
        when {
            descriptor.endsWith(")V") -> mv?.visitInsn(Opcodes.RETURN)
            descriptor.endsWith(")I") || descriptor.endsWith(")Z") || descriptor.endsWith(")B") ||
                descriptor.endsWith(")S") || descriptor.endsWith(")C") -> {
                mv?.visitInsn(Opcodes.ICONST_0)
                mv?.visitInsn(Opcodes.IRETURN)
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
