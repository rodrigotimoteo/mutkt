package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MutatorApplyTest {
    @Test
    fun `ARITHMETIC apply changes IADD to ISUB`() {
        val bytes = buildClassWithIAdd()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val arithMutation = mutations.first { it.operator == MutationOperator.ARITHMETIC }
        val mutated = mutator.applyMutation(bytes, arithMutation)
        assertTrue(mutated.isNotEmpty())
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ISUB in opcodes, "Expected ISUB in mutated bytecode")
        assertTrue(Opcodes.IADD !in opcodes, "Expected IADD to be removed")
    }

    @Test
    fun `NEGATE_CONDITIONALS apply changes IFEQ to IFNE`() {
        val bytes = buildClassWithIfEq()
        val mutator = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first()
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.IFNE in opcodes)
        assertTrue(Opcodes.IFEQ !in opcodes)
    }

    @Test
    fun `RETURN_VALS apply mutates IRETURN to constant return`() {
        val bytes = buildClassWithIReturn()
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ICONST_0 in opcodes)
    }

    @Test
    fun `NULL_RETURNS apply mutates ARETURN to ACONST_NULL plus ARETURN`() {
        val bytes = buildClassWithAReturn()
        val mutator = Mutator(setOf(MutationOperator.NULL_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.operator == MutationOperator.NULL_RETURNS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    @Test
    fun `applyMutation on missing lineNumber returns original bytes`() {
        val bytes = buildClassWithIAdd()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val fake = mutations.first().copy(lineNumber = 99999)
        val result = mutator.applyMutation(bytes, fake)
        assertEquals(bytes.toList(), result.toList())
    }

    @Test
    fun `applyMutation on wrong className still applies mutation`() {
        val bytes = buildClassWithIAdd()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val fake = mutations.first().copy(className = "wrong.Class")
        val result = mutator.applyMutation(bytes, fake)
        // className is not checked by the applier, so mutation is still applied
        assertNotEquals(bytes.toList(), result.toList())
    }

    @Test
    fun `applyMutation does not mutate input bytes`() {
        val bytes = buildClassWithIAdd()
        val original = bytes.toList()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        mutator.applyMutation(bytes, mutations.first())
        assertEquals(original, bytes.toList(), "Input bytes should not be modified")
    }

    private fun buildClassWithIAdd(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithIfEq(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "(I)I", null, null)
        mv?.visitCode()
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        val l = Label()
        mv?.visitJumpInsn(Opcodes.IFEQ, l)
        mv?.visitInsn(Opcodes.ICONST_1)
        val l2 = Label()
        mv?.visitJumpInsn(Opcodes.GOTO, l2)
        mv?.visitLabel(l)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitLabel(l2)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 2)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithIReturn(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithAReturn(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()Ljava/lang/String;", null, null)
        mv?.visitCode()
        mv?.visitLdcInsn("hello")
        mv?.visitInsn(Opcodes.ARETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
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
                    }
                }
            },
            0,
        )
        return opcodes
    }
}
