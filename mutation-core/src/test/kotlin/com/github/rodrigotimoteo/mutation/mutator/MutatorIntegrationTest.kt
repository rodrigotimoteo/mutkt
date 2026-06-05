package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MutatorIntegrationTest {
    @Test
    fun `scan then apply produces a different valid class`() {
        val bytes = buildCalculatorLikeClass()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertNotEquals(bytes.toList(), mutated.toList(), "Mutation should change bytecode")
    }

    @Test
    fun `multiple mutations can be applied to same class`() {
        val bytes = buildCalculatorLikeClass()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.size >= 2, "Need at least 2 mutations")

        val m1 = mutator.applyMutation(bytes, mutations[0])
        val m2 = mutator.applyMutation(bytes, mutations[1])
        assertNotEquals(bytes.toList(), m1.toList())
        assertNotEquals(bytes.toList(), m2.toList())
    }

    @Test
    fun `Kotlin class with Metadata is detected`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Kotlin", null, "java/lang/Object", null)
        val annV = cw.visitAnnotation("Lkotlin/Metadata;", true)
        annV?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val mutations = Mutator(MutationOperator.MVP_OPERATORS).scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Kotlin class should still generate mutations")
    }

    @Test
    fun `empty enabledOperators set generates no mutations`() {
        val bytes = buildCalculatorLikeClass()
        val mutations = Mutator(emptySet()).scanMutations(bytes)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `QUICK_WIN_OPERATORS scan generates void slash non-void call mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        // Call System.out.println("hello") - void method call
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

    private fun buildCalculatorLikeClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Calc", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv?.visitCode()
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitVarInsn(Opcodes.ILOAD, 2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 3)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
