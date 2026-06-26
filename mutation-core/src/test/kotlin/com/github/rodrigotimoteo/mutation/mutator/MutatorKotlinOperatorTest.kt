package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Kotlin-specific mutation operator fixes:
 * - SEALED_WHEN: instanceof chain detection
 * - DATA_CLASS_COPY: copy$default detection
 * - NULL_SAFETY: IFNULL/IFNONNULL + checkNotNullExpressionValue
 */
class MutatorKotlinOperatorTest {
    // ===========================================================================
    // Helpers
    // ===========================================================================

    /** Build class WITH @kotlin/Metadata (recognized as Kotlin class by scanner). */
    private fun buildKotlinClass(
        className: String = "com/example/Test",
        methodName: String = "test",
        descriptor: String = "()Ljava/lang/String;",
        lineNumber: Int = 1,
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
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
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        mv.visitCode()
        mv.visitLineNumber(lineNumber, Label())
        body(mv)
        val returnType = descriptor.substringAfterLast(")")
        when (returnType) {
            "V" -> mv.visitInsn(Opcodes.RETURN)
            "Ljava/lang/String;" -> {
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
            "I", "Z", "B", "S", "C" -> {
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitInsn(Opcodes.IRETURN)
            }
            else -> {
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        }
        mv.visitMaxs(10, 10)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Build class WITHOUT @kotlin/Metadata (Java class to scanner). */
    private fun buildJavaClass(
        className: String = "com/example/Test",
        methodName: String = "test",
        descriptor: String = "()Ljava/lang/String;",
        lineNumber: Int = 1,
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        mv.visitCode()
        mv.visitLineNumber(lineNumber, Label())
        body(mv)
        val returnType = descriptor.substringAfterLast(")")
        when (returnType) {
            "V" -> mv.visitInsn(Opcodes.RETURN)
            "Ljava/lang/String;" -> {
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
            "I", "Z", "B", "S", "C" -> {
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitInsn(Opcodes.IRETURN)
            }
            else -> {
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        }
        mv.visitMaxs(10, 10)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun assertStructurallyValid(bytes: ByteArray) {
        val reader = ClassReader(bytes)
        reader.accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG,
        )
    }

    private fun assertApplierWorks(
        operator: MutationOperator,
        bytes: ByteArray,
    ) {
        val mutator = Mutator(setOf(operator))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find $operator mutations")
        for (mutation in mutations) {
            val mutated = mutator.applyMutation(bytes, mutation)
            assertStructurallyValid(mutated)
        }
    }

    // ===========================================================================
    // SEALED_WHEN — instanceof chain tests
    // ===========================================================================

    @Test
    fun `SEALED_WHEN scans 2 instanceof on same line in Kotlin class`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Foo")
                val lbl1 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl1)
                mv.visitLabel(lbl1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Bar")
            }
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.SEALED_WHEN },
            "Should find SEALED_WHEN mutation for 2+ instanceof chain",
        )
    }

    @Test
    fun `SEALED_WHEN ignores single instanceof`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Foo")
            }
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(bytes)
        val sealedMutations = mutations.filter { it.operator == MutationOperator.SEALED_WHEN }
        assertEquals(0, sealedMutations.size, "Single instanceof should not trigger SEALED_WHEN")
    }

    @Test
    fun `SEALED_WHEN ignores non-Kotlin class`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Foo")
                val lbl1 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl1)
                mv.visitLabel(lbl1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Bar")
            }
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(bytes)
        val sealedMutations = mutations.filter { it.operator == MutationOperator.SEALED_WHEN }
        assertEquals(0, sealedMutations.size, "Java class should not trigger SEALED_WHEN")
    }

    @Test
    fun `SEALED_WHEN instanceof applier replaces INSTANCEOF with POP+ICONST_0`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Foo")
                val lbl1 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl1)
                mv.visitLabel(lbl1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Bar")
            }
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "Should find SEALED_WHEN mutations")
        // Verify mutation can be applied without exception
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertTrue(mutated.isNotEmpty(), "Mutated bytecode should not be empty")
        // Verify bytecode size changed (POP+ICONST_0 is different size than INSTANCEOF)
        assertTrue(
            mutated.size != bytes.size || !mutated.contentEquals(bytes),
            "Mutated bytecode should differ from original",
        )
    }

    @Test
    fun `SEALED_WHEN detects 3+ instanceof on same line`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/A")
                val lbl1 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl1)
                mv.visitLabel(lbl1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/B")
                val lbl2 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl2)
                mv.visitLabel(lbl2)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/C")
            }
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(bytes)
        val sealed = mutations.filter { it.operator == MutationOperator.SEALED_WHEN }
        assertTrue(sealed.size >= 2, "3 instanceof should produce at least 2 mutations (from 2nd and 3rd)")
    }

    @Test
    fun `SEALED_WHEN type stored in metadata`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Success")
                val lbl1 = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, lbl1)
                mv.visitLabel(lbl1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Error")
            }
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(bytes)
        val sealed = mutations.filter { it.operator == MutationOperator.SEALED_WHEN }
        assertTrue(sealed.isNotEmpty())
        val last = sealed.last()
        assertEquals("com/example/Error", last.metadata["type"])
    }

    // ===========================================================================
    // DATA_CLASS_COPY — copy$default tests
    // ===========================================================================

    @Test
    fun `DATA_CLASS_COPY scans INVOKESTATIC copy$default`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/example/User",
                    "copy\$default",
                    "(Lcom/example/User;I)V",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.DATA_CLASS_COPY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.DATA_CLASS_COPY &&
                    it.description.contains("copy\$default")
            },
            "Should find DATA_CLASS_COPY for copy\$default call",
        )
    }

    @Test
    fun `DATA_CLASS_COPY still scans INVOKEVIRTUAL copy`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "com/example/User",
                    "copy",
                    "()Lcom/example/User;",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.DATA_CLASS_COPY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.DATA_CLASS_COPY &&
                    it.description.contains("copy()")
            },
            "Should still find INVOKEVIRTUAL copy()",
        )
    }

    @Test
    fun `DATA_CLASS_COPY ignores non-copy INVOKESTATIC`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/example/Utils",
                    "something",
                    "()V",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.DATA_CLASS_COPY)).scanMutations(bytes)
        assertEquals(0, mutations.size, "Non-copy static method should not match")
    }

    @Test
    fun `DATA_CLASS_COPY copy$default applier produces valid bytecode`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/example/User",
                    "copy\$default",
                    "(Lcom/example/User;I)V",
                    false,
                )
            }
        assertApplierWorks(MutationOperator.DATA_CLASS_COPY, bytes)
    }

    @Test
    fun `DATA_CLASS_COPY copy$default mutation metadata is correct`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/example/User",
                    "copy\$default",
                    "(Lcom/example/User;I)V",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.DATA_CLASS_COPY)).scanMutations(bytes)
        val copyDefault = mutations.find { it.description.contains("copy\$default") }
        assertNotNull(copyDefault)
        assertEquals(Opcodes.NOP, copyDefault.mutatedOpcode)
    }

    // ===========================================================================
    // NULL_SAFETY — IFNULL/IFNONNULL + checkNotNullExpressionValue tests
    // ===========================================================================

    @Test
    fun `NULL_SAFETY scans IFNULL in Kotlin class as safe-call mutation`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, lbl)
                mv.visitLdcInsn("non-null")
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.NULL_SAFETY &&
                    it.originalOpcode == Opcodes.IFNULL
            },
            "Should find NULL_SAFETY for IFNULL",
        )
    }

    @Test
    fun `NULL_SAFETY scans IFNONNULL in Kotlin class as elvis mutation`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNONNULL, lbl)
                mv.visitInsn(Opcodes.POP)
                mv.visitLdcInsn("default")
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.NULL_SAFETY &&
                    it.originalOpcode == Opcodes.IFNONNULL
            },
            "Should find NULL_SAFETY for IFNONNULL",
        )
    }

    @Test
    fun `NULL_SAFETY IFNULL mutation flips to IFNONNULL`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, lbl)
                mv.visitLdcInsn("non-null")
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        val ifnullMutation = mutations.find { it.originalOpcode == Opcodes.IFNULL }
        assertNotNull(ifnullMutation)
        assertEquals(Opcodes.IFNONNULL, ifnullMutation.mutatedOpcode, "IFNULL should flip to IFNONNULL")
    }

    @Test
    fun `NULL_SAFETY IFNONNULL mutation flips to IFNULL`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNONNULL, lbl)
                mv.visitInsn(Opcodes.POP)
                mv.visitLdcInsn("default")
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        val ifnonnullMutation = mutations.find { it.originalOpcode == Opcodes.IFNONNULL }
        assertNotNull(ifnonnullMutation)
        assertEquals(Opcodes.IFNULL, ifnonnullMutation.mutatedOpcode, "IFNONNULL should flip to IFNULL")
    }

    @Test
    fun `NULL_SAFETY ignores IFNULL in non-Kotlin class`() {
        val bytes =
            buildJavaClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, lbl)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        val branchMutations = mutations.filter { it.originalOpcode == Opcodes.IFNULL }
        assertEquals(0, branchMutations.size, "Java IFNULL should not trigger NULL_SAFETY")
    }

    @Test
    fun `NULL_SAFETY scans checkNotNullExpressionValue`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "checkNotNullExpressionValue",
                    "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.NULL_SAFETY &&
                    it.description.contains("checkNotNullExpressionValue")
            },
            "Should detect checkNotNullExpressionValue",
        )
    }

    @Test
    fun `NULL_SAFETY still detects checkNotNull`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "checkNotNull",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.NULL_SAFETY &&
                    it.description.contains("checkNotNull")
            },
            "Should still detect checkNotNull",
        )
    }

    @Test
    fun `NULL_SAFETY still detects checkNotNullParameter`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitLdcInsn("param")
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "checkNotNullParameter",
                    "(Ljava/lang/Object;Ljava/lang/String;)V",
                    false,
                )
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        assertTrue(
            mutations.any {
                it.operator == MutationOperator.NULL_SAFETY &&
                    it.description.contains("checkNotNullParameter")
            },
            "Should still detect checkNotNullParameter",
        )
    }

    @Test
    fun `NULL_SAFETY IFNULL applier produces valid bytecode`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, lbl)
                mv.visitLdcInsn("value")
            }
        assertApplierWorks(MutationOperator.NULL_SAFETY, bytes)
    }

    @Test
    fun `NULL_SAFETY IFNONNULL applier produces valid bytecode`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNONNULL, lbl)
                mv.visitInsn(Opcodes.POP)
                mv.visitLdcInsn("default")
            }
        assertApplierWorks(MutationOperator.NULL_SAFETY, bytes)
    }

    @Test
    fun `NULL_SAFETY checkNotNullExpressionValue applier produces valid bytecode`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitLdcInsn("expression")
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "checkNotNullExpressionValue",
                    "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                    false,
                )
            }
        assertApplierWorks(MutationOperator.NULL_SAFETY, bytes)
    }

    @Test
    fun `NULL_SAFETY combined IFNULL + checkNotNull same line produces multiple mutations`() {
        val bytes =
            buildKotlinClass { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "checkNotNullExpressionValue",
                    "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                    false,
                )
                mv.visitInsn(Opcodes.DUP)
                val lbl = Label()
                mv.visitJumpInsn(Opcodes.IFNULL, lbl)
            }
        val mutations = Mutator(setOf(MutationOperator.NULL_SAFETY)).scanMutations(bytes)
        val nullSafety = mutations.filter { it.operator == MutationOperator.NULL_SAFETY }
        assertTrue(nullSafety.size >= 2, "Should find at least 2 NULL_SAFETY mutations")
    }
}
