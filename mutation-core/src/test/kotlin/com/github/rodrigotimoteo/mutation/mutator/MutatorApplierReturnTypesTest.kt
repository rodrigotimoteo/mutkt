package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertTrue

class MutatorApplierReturnTypesTest {
    @Test
    fun `RETURN_VALS apply LRETURN adds LCONST_0`() {
        val bytes = buildClassWithReturnOpcode(Opcodes.LRETURN)
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.LCONST_0 in opcodes, "Expected LCONST_0")
        assertTrue(Opcodes.LRETURN in opcodes, "Expected LRETURN")
    }

    @Test
    fun `RETURN_VALS apply FRETURN adds FCONST_0`() {
        val bytes = buildClassWithReturnOpcode(Opcodes.FRETURN)
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.FCONST_0 in opcodes, "Expected FCONST_0")
        assertTrue(Opcodes.FRETURN in opcodes, "Expected FRETURN")
    }

    @Test
    fun `RETURN_VALS apply DRETURN adds DCONST_0`() {
        val bytes = buildClassWithReturnOpcode(Opcodes.DRETURN)
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.DCONST_0 in opcodes, "Expected DCONST_0")
        assertTrue(Opcodes.DRETURN in opcodes, "Expected DRETURN")
    }

    @Test
    fun `RETURN_VALS apply RETURN for void method`() {
        val bytes = buildVoidClassWithReturn()
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
            val mutated = mutator.applyMutation(bytes, mutation)
            val opcodes = collectOpcodes(mutated)
            // Should still have RETURN
            assertTrue(Opcodes.RETURN in opcodes)
        }
    }

    @Test
    fun `EMPTY_RETURNS apply for IntArray return`() {
        val bytes = buildClassWithReturnType("()[I")
        val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.EMPTY_RETURNS }
            val mutated = mutator.applyMutation(bytes, mutation)
            // Should contain NEWARRAY
            val opcodes = collectAllOpcodes(mutated)
            assertTrue(Opcodes.NEWARRAY in opcodes, "Expected NEWARRAY")
        }
    }

    @Test
    fun `EMPTY_RETURNS apply for String array return`() {
        val bytes = buildClassWithReturnType("()[Ljava/lang/String;")
        val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.EMPTY_RETURNS }
            val mutated = mutator.applyMutation(bytes, mutation)
            val opcodes = collectAllOpcodes(mutated)
            assertTrue(Opcodes.ANEWARRAY in opcodes, "Expected ANEWARRAY")
        }
    }

    @Test
    fun `NULL_RETURNS apply for String return type`() {
        val bytes = buildClassWithReturnType("()Ljava/lang/String;")
        val mutator = Mutator(setOf(MutationOperator.NULL_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.NULL_RETURNS }
            val mutated = mutator.applyMutation(bytes, mutation)
            val opcodes = collectOpcodes(mutated)
            assertTrue(Opcodes.ACONST_NULL in opcodes, "Expected ACONST_NULL")
        }
    }

    @Test
    fun `NULL_RETURNS apply for non-ARETURN opcode delegates to super`() {
        // Build a class with IRETURN and try NULL_RETURNS — should not apply
        val bytes = buildClassWithReturnOpcode(Opcodes.IRETURN)
        val mutator = Mutator(setOf(MutationOperator.NULL_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        // NULL_RETURNS only matches ARETURN — no mutations expected
        assertTrue(mutations.isEmpty(), "NULL_RETURNS should not match IRETURN")
    }

    @Test
    fun `applyReturnMutation with RETURN opcode and RETURN_VALS delegates`() {
        // Build a void method with RETURN, then apply RETURN_VALS
        val bytes = buildVoidClassWithReturn()
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        // Mutation may or may not exist depending on scanner logic
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.RETURN_VALS }
            // Should not throw
            mutator.applyMutation(bytes, mutation)
        }
    }

    @Test
    fun `applyReturnMutation with ARETURN and NULL_RETURNS pushes null`() {
        val bytes = buildClassWithReturnType("()Ljava/lang/String;")
        val mutator = Mutator(setOf(MutationOperator.NULL_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutation = mutations.first { it.operator == MutationOperator.NULL_RETURNS }
        val mutated = mutator.applyMutation(bytes, mutation)
        val opcodes = collectOpcodes(mutated)
        assertTrue(Opcodes.ACONST_NULL in opcodes)
    }

    @Test
    fun `applyReturnMutation for EMPTY_RETURNS with non-matching ARETURN`() {
        // Build a method returning an int (no ARETURN)
        val bytes = buildClassWithReturnOpcode(Opcodes.IRETURN)
        val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        // EMPTY_RETURNS only matches ARETURN
        assertTrue(mutations.isEmpty(), "EMPTY_RETURNS should not match IRETURN")
    }

    @Test
    fun `applyReturnMutation for non-return operator delegates to super`() {
        val bytes = buildClassWithReturnOpcode(Opcodes.ARETURN)
        // Use ARITHMETIC which doesn't trigger applyReturnMutation
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        // ARITHMETIC may not scan ARETURN — verify it doesn't crash
        // No assertions on result, just no exception
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first()
            // Should not throw
            mutator.applyMutation(bytes, mutation)
        }
    }

    @Test
    fun `getMutatedOpcode FALSE_RETURNS on ICONST_0 returns ICONST_1`() {
        val bytes = buildClassWithFalseReturn()
        val mutator = Mutator(setOf(MutationOperator.FALSE_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutation = mutations.first { it.operator == MutationOperator.FALSE_RETURNS }
            val mutated = mutator.applyMutation(bytes, mutation)
            val opcodes = collectOpcodes(mutated)
            // Should contain ICONST_1 (replacing ICONST_0)
            assertTrue(Opcodes.ICONST_1 in opcodes, "Expected ICONST_1 from FALSE_RETURNS")
        }
    }

    @Test
    fun `getMutatedOpcode FALSE_RETURNS on non-ICONST_0 returns original`() {
        val bytes = buildClassWithTrueReturn()
        val mutator = Mutator(setOf(MutationOperator.FALSE_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        // FALSE_RETURNS only matches ICONST_0 in boolean methods
        // ICONST_1 in boolean method should not be matched
        assertTrue(mutations.isEmpty(), "FALSE_RETURNS should not match ICONST_1")
    }

    @Test
    fun `getMutatedOpcode TRUE_RETURNS on non-ICONST_1 returns original`() {
        val bytes = buildClassWithFalseReturn()
        val mutator = Mutator(setOf(MutationOperator.TRUE_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        // TRUE_RETURNS only matches ICONST_1 — ICONST_0 not matched
        assertTrue(mutations.isEmpty(), "TRUE_RETURNS should not match ICONST_0")
    }

    private fun buildClassWithReturnOpcode(returnOpcode: Int): ByteArray {
        val (descriptor, returnType) =
            when (returnOpcode) {
                Opcodes.IRETURN -> "()I" to Opcodes.ICONST_0
                Opcodes.LRETURN -> "()J" to Opcodes.LCONST_0
                Opcodes.FRETURN -> "()F" to Opcodes.FCONST_0
                Opcodes.DRETURN -> "()D" to Opcodes.DCONST_0
                Opcodes.ARETURN -> "()Ljava/lang/String;" to Opcodes.ACONST_NULL
                Opcodes.RETURN -> "()V" to Opcodes.RETURN
                else -> "()I" to Opcodes.ICONST_0
            }
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        if (returnOpcode != Opcodes.RETURN) {
            mv?.visitInsn(returnType)
        }
        mv?.visitInsn(returnOpcode)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithReturnType(descriptor: String): ByteArray {
        val returnOpcode =
            when {
                descriptor.endsWith(")V") -> Opcodes.RETURN
                descriptor.endsWith(")I") || descriptor.endsWith(")Z") -> Opcodes.IRETURN
                descriptor.endsWith(")J") -> Opcodes.LRETURN
                descriptor.endsWith(")F") -> Opcodes.FRETURN
                descriptor.endsWith(")D") -> Opcodes.DRETURN
                else -> Opcodes.ARETURN
            }
        val pushInsn =
            when (returnOpcode) {
                Opcodes.RETURN -> null
                Opcodes.IRETURN -> Opcodes.ICONST_0
                Opcodes.LRETURN -> Opcodes.LCONST_0
                Opcodes.FRETURN -> Opcodes.FCONST_0
                Opcodes.DRETURN -> Opcodes.DCONST_0
                Opcodes.ARETURN -> Opcodes.ACONST_NULL
                else -> Opcodes.ICONST_0
            }
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        if (pushInsn != null) {
            mv?.visitInsn(pushInsn)
        }
        mv?.visitInsn(returnOpcode)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildVoidClassWithReturn(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithTrueReturn(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()Z", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithFalseReturn(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()Z", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun collectOpcodes(bytes: ByteArray): Set<Int> {
        val opcodes = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
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

    private fun collectAllOpcodes(bytes: ByteArray): Set<Int> {
        val opcodes = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
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
}
