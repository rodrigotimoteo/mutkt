package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutatorSuppressionTest {
    @Test
    fun `classSuppressed suppresses all mutations`() {
        val bytes = buildClassWithClassLevelSuppression()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size, "Class-level @SuppressMutations should suppress all")
    }

    @Test
    fun `operator suppression on class suppresses all mutations`() {
        // Build a class with @SuppressMutations(operators=["ARITHMETIC"])
        val bytes = buildClassWithOperatorSuppression(listOf("ARITHMETIC"))
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // The annotation's presence suppresses all mutations
        assertEquals(0, mutations.size)
    }

    @Test
    fun `class without SuppressMutations annotation allows all operators`() {
        val bytes = buildClassWithIAdd()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // Some mutations should exist
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `class with empty operator list in SuppressMutations still suppresses all`() {
        // @SuppressMutations() with no operators is treated as suppress-all
        // (consistent with Java's @SuppressWarnings behavior)
        val bytes = buildClassWithOperatorSuppression(emptyList())
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // @SuppressMutations with no operators should suppress all
        assertEquals(0, mutations.size)
    }

    @Test
    fun `multiple suppressed operators all ignored`() {
        val bytes = buildClassWithOperatorSuppression(listOf("ARITHMETIC", "RETURN_VALS"))
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // The annotation's presence suppresses all, regardless of listed operators
        assertEquals(0, mutations.size)
    }

    @Test
    fun `unknown operator in SuppressMutations still suppresses all`() {
        // The annotation's presence alone is enough to suppress all mutations
        val bytes = buildClassWithOperatorSuppression(listOf("DOES_NOT_EXIST"))
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // Annotation present → suppress all
        assertEquals(0, mutations.size)
    }

    private fun buildClassWithIAdd(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithClassLevelSuppression(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        // Class-level @SuppressMutations with no operators — suppresses all
        val av = cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)
        av?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithOperatorSuppression(operators: List<String>): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val av = cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)
        if (operators.isNotEmpty()) {
            val arr = av?.visitArray("operators")
            for (op in operators) {
                arr?.visit(null, op)
            }
            arr?.visitEnd()
        }
        av?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
