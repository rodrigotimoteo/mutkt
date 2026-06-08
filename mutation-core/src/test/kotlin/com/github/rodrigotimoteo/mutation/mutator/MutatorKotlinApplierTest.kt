package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertTrue

/**
 * Bytecode validity tests for Kotlin-specific operator appliers.
 * Each test builds an ASM class with the target pattern, applies the mutation,
 * and verifies the mutated bytecode is structurally valid (ClassReader accepts it).
 */
class MutatorKotlinApplierTest {
    private fun assertStructurallyValid(bytes: ByteArray) {
        // ClassReader will throw if bytecode is structurally invalid
        val reader = ClassReader(bytes)
        // Also verify it can be visited without error
        reader.accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES,
        )
    }

    // ============== DATA_CLASS_COPY ==============

    @Test
    fun `DATA_CLASS_COPY applier produces structurally valid bytecode`() {
        val bytes = buildClassWithCopyMethod()
        val mutator = Mutator(setOf(MutationOperator.DATA_CLASS_COPY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find DATA_CLASS_COPY mutations")

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertStructurallyValid(mutated)
    }

    private fun buildClassWithCopyMethod(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/DataClass", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        // copy() method definition
        val copy = cw.visitMethod(Opcodes.ACC_PUBLIC, "copy", "()Lcom/example/DataClass;", null, null)
        copy.visitCode()
        copy.visitVarInsn(Opcodes.ALOAD, 0)
        copy.visitInsn(Opcodes.ARETURN)
        copy.visitMaxs(1, 1)
        copy.visitEnd()
        // Caller method that invokes copy() via INVOKEVIRTUAL
        val caller = cw.visitMethod(Opcodes.ACC_PUBLIC, "duplicate", "()Lcom/example/DataClass;", null, null)
        caller.visitCode()
        caller.visitLineNumber(1, org.objectweb.asm.Label())
        caller.visitVarInsn(Opcodes.ALOAD, 0)
        caller.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/example/DataClass", "copy", "()Lcom/example/DataClass;", false)
        caller.visitInsn(Opcodes.ARETURN)
        caller.visitMaxs(1, 1)
        caller.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    // ============== COROUTINE ==============

    @Test
    fun `COROUTINE applier produces structurally valid bytecode`() {
        val bytes = buildClassWithCoroutineCall()
        val mutator = Mutator(setOf(MutationOperator.COROUTINE))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find COROUTINE mutations")

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertStructurallyValid(mutated)
    }

    private fun buildClassWithCoroutineCall(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/CoroutineUser", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        // Method calling kotlinx.coroutines.BuildersKt.launch (matches scanner pattern)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        mv.visitInsn(Opcodes.ACONST_NULL) // scope
        mv.visitInsn(Opcodes.ACONST_NULL) // context
        mv.visitInsn(Opcodes.ACONST_NULL) // block
        val launchDesc =
            "(Lkotlinx/coroutines/CoroutineScope;" +
                "Lkotlin/coroutines/CoroutineContext;" +
                "Lkotlin/jvm/functions/Function2;)" +
                "Lkotlinx/coroutines/Job;"
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlinx/coroutines/BuildersKt",
            "launch",
            launchDesc,
            false,
        )
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    // ============== NULL_SAFETY ==============

    @Test
    fun `NULL_SAFETY applier produces structurally valid bytecode for void return`() {
        val bytes = buildClassWithVoidNullCheck()
        val mutator = Mutator(setOf(MutationOperator.NULL_SAFETY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find NULL_SAFETY mutations")

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertStructurallyValid(mutated)
    }

    @Test
    fun `NULL_SAFETY applier produces structurally valid bytecode for non-void return`() {
        val bytes = buildClassWithReturnNullCheck()
        val mutator = Mutator(setOf(MutationOperator.NULL_SAFETY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find NULL_SAFETY mutations for non-void")

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertStructurallyValid(mutated)
    }

    private fun buildClassWithVoidNullCheck(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/NullCheck", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        // Method calling Intrinsics.checkNotNullParameter (matches scanner pattern)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "greet", "(Ljava/lang/String;)V", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitLdcInsn("name")
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlin/jvm/internal/Intrinsics",
            "checkNotNullParameter",
            "(Ljava/lang/Object;Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithReturnNullCheck(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/NullCheck", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        // Method calling Intrinsics.checkNotNull (returns Object, matches scanner pattern)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "require", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlin/jvm/internal/Intrinsics",
            "checkNotNull",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
        )
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    // ============== SEALED_WHEN ==============

    @Test
    fun `SEALED_WHEN applier produces structurally valid bytecode for tableswitch`() {
        val bytes = buildClassWithTableSwitch()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find SEALED_WHEN mutations")

        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertStructurallyValid(mutated)
    }

    private fun buildClassWithTableSwitch(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/WhenExpr", null, "java/lang/Object", null)
        // Add @kotlin.Metadata annotation so scanner recognizes as Kotlin class
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "handle", "(I)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        val dflt = org.objectweb.asm.Label()
        val case0 = org.objectweb.asm.Label()
        val case1 = org.objectweb.asm.Label()
        val case2 = org.objectweb.asm.Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitTableSwitchInsn(0, 2, dflt, case0, case1, case2)
        mv.visitLabel(case0)
        mv.visitLdcInsn("zero")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitLabel(case1)
        mv.visitLdcInsn("one")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitLabel(case2)
        mv.visitLdcInsn("two")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitLabel(dflt)
        mv.visitLdcInsn("unknown")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
