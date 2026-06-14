package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Edge case tests for the split Mutator package.
 *
 * Covers:
 * - Public API contract (scan/apply/generate)
 * - isKotlinSyntheticMethod direct coverage (positive + negative)
 * - Malformed / empty / minimal class bytes
 * - Classes with only constructors or only static initializers
 * - Single-method class round-trip
 * - Combined enabledOperators + excludedMethods
 * - Reuse of Mutator instance across multiple class files
 */
class MutatorEdgeCasesTest {
    // ======================================================================
    // isKotlinSyntheticMethod — direct unit coverage
    // ======================================================================

    @Test
    fun `isKotlinSyntheticMethod matches copy$default`() {
        assertTrue(isKotlinSyntheticMethod("copy\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod matches componentN$default for any N`() {
        for (n in 1..5) {
            assertTrue(
                isKotlinSyntheticMethod("component$n\$default"),
                "component$n\$default should be synthetic",
            )
        }
    }

    @Test
    fun `isKotlinSyntheticMethod matches $serializer suffix`() {
        assertTrue(isKotlinSyntheticMethod("Foo\$serializer"))
        assertTrue(isKotlinSyntheticMethod("Bar\$serializer"))
    }

    @Test
    fun `isKotlinSyntheticMethod rejects plain copy without $default`() {
        assertFalse(isKotlinSyntheticMethod("copy"), "plain 'copy' is not synthetic")
    }

    @Test
    fun `isKotlinSyntheticMethod rejects plain componentN without $default`() {
        assertFalse(isKotlinSyntheticMethod("component1"), "component1 is not synthetic")
        assertFalse(isKotlinSyntheticMethod("component2"), "component2 is not synthetic")
    }

    @Test
    fun `isKotlinSyntheticMethod rejects getCompanion`() {
        assertFalse(isKotlinSyntheticMethod("getCompanion"))
    }

    @Test
    fun `isKotlinSyntheticMethod rejects invokeSuspend`() {
        assertFalse(isKotlinSyntheticMethod("invokeSuspend"))
    }

    @Test
    fun `isKotlinSyntheticMethod rejects normal user methods`() {
        val normal = listOf("calculate", "toString", "hashCode", "equals", "main", "run", "invoke")
        for (name in normal) {
            assertFalse(isKotlinSyntheticMethod(name), "'$name' should not be synthetic")
        }
    }

    @Test
    fun `isKotlinSyntheticMethod matches toString$default hashCode$default equals$default`() {
        assertTrue(isKotlinSyntheticMethod("toString\$default"))
        assertTrue(isKotlinSyntheticMethod("hashCode\$default"))
        assertTrue(isKotlinSyntheticMethod("equals\$default"))
    }

    // ======================================================================
    // Public API: scanMutations + applyMutation still work after the split
    // ======================================================================

    @Test
    fun `scanMutations and applyMutation round-trip produces valid bytecode`() {
        val bytes = buildSimpleArithmeticClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty(), "scanner should find at least one mutation")
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertTrue(mutated.isNotEmpty())
        assertNotEquals(bytes.toList(), mutated.toList())
        // Mutated bytecode must still parse
        ClassReader(mutated).accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES,
        )
    }

    @Test
    fun `generateMutants produces non-empty list for non-trivial class`() {
        val bytes = buildSimpleArithmeticClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutants = mutator.generateMutants(bytes)
        assertTrue(mutants.isNotEmpty(), "should generate at least one mutant")
        // Every emitted mutant must differ from the input bytes
        for ((_, mutated) in mutants) {
            assertNotEquals(bytes.toList(), mutated.toList())
        }
    }

    @Test
    fun `generateMutants with empty operators returns empty list`() {
        val bytes = buildSimpleArithmeticClass()
        val mutator = Mutator(emptySet())
        assertEquals(0, mutator.generateMutants(bytes).size)
    }

    @Test
    fun `applyMutation with empty enabledOperators is a no-op`() {
        val bytes = buildSimpleArithmeticClass()
        val mutator = Mutator(emptySet())
        // Craft a fake mutation — applier should not have a matching branch
        val fake =
            MutationInfo(
                operator = MutationOperator.ARITHMETIC,
                className = "Test",
                methodName = "test",
                methodDescriptor = "()I",
                lineNumber = 1,
                description = "fake",
                originalOpcode = Opcodes.IADD,
                mutatedOpcode = Opcodes.IADD,
            )
        val result = mutator.applyMutation(bytes, fake)
        // Should still produce a valid class (no real change since IADD→IADD)
        ClassReader(result).accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES,
        )
    }

    // ======================================================================
    // Malformed / minimal class bytes
    // ======================================================================

    @Test
    fun `scanMutations on truly empty bytes throws (ASM behavior)`() {
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        // ASM's ClassReader requires at least 7 bytes for the class header
        val thrown =
            runCatching { mutator.scanMutations(ByteArray(0)) }
        assertTrue(
            thrown.isFailure,
            "Empty bytes should trigger ASM rejection (documented behavior)",
        )
    }

    @Test
    fun `scanMutations on garbage bytes throws (ASM behavior)`() {
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        // Random non-class bytes — ASM rejects malformed class files
        val garbage = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val thrown =
            runCatching { mutator.scanMutations(garbage) }
        assertTrue(
            thrown.isFailure,
            "Garbage bytes should trigger ASM rejection (documented behavior)",
        )
    }

    // ======================================================================
    // Class with only <init> constructor — no user methods
    // ======================================================================

    @Test
    fun `class with only init constructor generates no mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "OnlyInit", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        val mutations =
            Mutator(MutationOperator.ALL_OPERATORS).scanMutations(cw.toByteArray())
        assertEquals(0, mutations.size, "init-only class should not produce mutations")
    }

    @Test
    fun `class with only clinit static initializer generates no mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "OnlyClinit", null, "java/lang/Object", null)
        val clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        clinit?.visitCode()
        clinit?.visitInsn(Opcodes.RETURN)
        clinit?.visitMaxs(0, 0)
        clinit?.visitEnd()
        cw.visitEnd()
        val mutations =
            Mutator(MutationOperator.ALL_OPERATORS).scanMutations(cw.toByteArray())
        assertEquals(
            0,
            mutations.size,
            "clinit-only class should not produce mutations",
        )
    }

    // ======================================================================
    // Single-method class
    // ======================================================================

    @Test
    fun `single-method class round-trips through scan and apply`() {
        val bytes = buildSingleMethodClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        assertEquals(1, mutations.size, "single arithmetic method → exactly 1 mutation")
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertNotEquals(bytes.toList(), mutated.toList())
    }

    // ======================================================================
    // Reuse of Mutator instance across multiple classes
    // ======================================================================

    @Test
    fun `Mutator instance is reusable across multiple classes`() {
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val bytes1 = buildSimpleArithmeticClass()
        val bytes2 = buildSingleMethodClass()
        val m1 = mutator.scanMutations(bytes1)
        val m2 = mutator.scanMutations(bytes2)
        assertTrue(m1.isNotEmpty())
        assertTrue(m2.isNotEmpty())
        // Apply on each — should not pollute state
        val out1 = mutator.applyMutation(bytes1, m1.first())
        val out2 = mutator.applyMutation(bytes2, m2.first())
        assertNotEquals(bytes1.toList(), out1.toList())
        assertNotEquals(bytes2.toList(), out2.toList())
    }

    // ======================================================================
    // Combined enabledOperators + excludedMethods
    // ======================================================================

    @Test
    fun `combined enabledOperators and excludedMethods filter correctly`() {
        val mutator =
            Mutator(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                excludedMethods = setOf("compute"),
            )
        val bytes =
            buildClassWithTwoMethods() // contains "compute" (excluded) and "run" (included)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.none { it.methodName == "compute" })
        assertTrue(mutations.any { it.methodName == "run" })
    }

    // ======================================================================
    // All 16 active operators present
    // ======================================================================

    @Test
    fun `ALL_OPERATORS contains 16 active operators (INVERT_NEGS excluded)`() {
        val all = MutationOperator.ALL_OPERATORS
        assertEquals(16, all.size, "Expected 16 active operators")
        assertFalse(MutationOperator.INVERT_NEGS in all, "Deprecated INVERT_NEGS excluded")
    }

    // ======================================================================
    // Non-Kotlin class with switch — SEALED_WHEN must not fire
    // ======================================================================

    @Test
    fun `SEALED_WHEN does not fire on non-Kotlin class with switch`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            Opcodes.ASM9,
            Opcodes.ACC_PUBLIC,
            "NonKotlinSwitch",
            null,
            "java/lang/Object",
            null,
        )
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_0)
        val dflt = Label()
        val c0 = Label()
        mv?.visitLookupSwitchInsn(dflt, intArrayOf(0), arrayOf(c0))
        mv?.visitLabel(c0)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(dflt)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        val mutations =
            Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(cw.toByteArray())
        assertEquals(0, mutations.size, "Non-Kotlin switch must not trigger SEALED_WHEN")
    }

    // ======================================================================
    // isKotlinSyntheticMethod rejection of similar-but-not-exact names
    // ======================================================================

    @Test
    fun `isKotlinSyntheticMethod rejects names with extra suffix`() {
        // Names that *contain* a synthetic marker but are not exactly synthetic
        assertFalse(isKotlinSyntheticMethod("myCopy\$default"))
        assertFalse(isKotlinSyntheticMethod("compute\$default"))
        assertFalse(isKotlinSyntheticMethod("fooSerializer"))
    }

    // ======================================================================
    // MutationInfo metadata defaults to empty map
    // ======================================================================

    @Test
    fun `MutationInfo metadata defaults to empty map`() {
        val info =
            MutationInfo(
                operator = MutationOperator.ARITHMETIC,
                className = "C",
                methodName = "m",
                methodDescriptor = "()V",
                lineNumber = 1,
                description = "d",
                originalOpcode = 0,
                mutatedOpcode = 0,
            )
        assertNotNull(info.metadata)
        assertEquals(0, info.metadata.size)
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private fun buildSimpleArithmeticClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Arith", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildSingleMethodClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Single", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.ISUB)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithTwoMethods(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Two", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        for (name in listOf("compute", "run")) {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()I", null, null)
            mv?.visitCode()
            mv?.visitLineNumber(1, Label())
            mv?.visitInsn(Opcodes.ICONST_1)
            mv?.visitInsn(Opcodes.ICONST_2)
            mv?.visitInsn(Opcodes.IADD)
            mv?.visitInsn(Opcodes.IRETURN)
            mv?.visitMaxs(2, 1)
            mv?.visitEnd()
        }
        cw.visitEnd()
        return cw.toByteArray()
    }
}
