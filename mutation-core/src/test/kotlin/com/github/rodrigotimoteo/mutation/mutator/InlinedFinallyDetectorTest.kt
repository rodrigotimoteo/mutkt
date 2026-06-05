package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InlinedFinallyDetectorTest {
    @Test
    fun `detect on empty class returns empty list`() {
        val detector = InlinedFinallyDetector()
        val bytes = buildEmptyClass()
        val result = detector.detect(bytes)
        assertEquals(0, result.size)
    }

    @Test
    fun `detect on class with no methods returns empty list`() {
        val detector = InlinedFinallyDetector()
        val bytes = buildClassWithNoMethods()
        val result = detector.detect(bytes)
        assertEquals(0, result.size)
    }

    @Test
    fun `detect on class with simple method returns empty list`() {
        val detector = InlinedFinallyDetector()
        val bytes = buildClassWithSimpleMethod()
        val result = detector.detect(bytes)
        assertEquals(0, result.size)
    }

    @Test
    fun `detect on class with try-finally returns empty for simple finally`() {
        val detector = InlinedFinallyDetector()
        val bytes = buildClassWithSimpleFinally()
        val result = detector.detect(bytes)
        // Simple finally without inlining (single exit point) returns empty
        assertEquals(0, result.size)
    }

    @Test
    fun `InlinedFinallyBlock data class equality`() {
        val a = InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15))
        val b = InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `InlinedFinallyBlock preserves all fields`() {
        val block =
            InlinedFinallyDetector.InlinedFinallyBlock(
                methodName = "method1",
                startLine = 10,
                endLine = 20,
                handlerLine = 15,
                duplicatedAt = listOf(5, 10, 15, 20),
            )
        assertEquals("method1", block.methodName)
        assertEquals(10, block.startLine)
        assertEquals(20, block.endLine)
        assertEquals(15, block.handlerLine)
        assertEquals(listOf(5, 10, 15, 20), block.duplicatedAt)
    }

    @Test
    fun `isInInlinedBlock returns true for line within block`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
            )
        assertTrue(detector.isInInlinedBlock(10, blocks))
        assertTrue(detector.isInInlinedBlock(15, blocks))
        assertTrue(detector.isInInlinedBlock(20, blocks))
    }

    @Test
    fun `isInInlinedBlock returns false for line outside block`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
            )
        assertFalse(detector.isInInlinedBlock(5, blocks))
        assertFalse(detector.isInInlinedBlock(25, blocks))
        assertFalse(detector.isInInlinedBlock(100, blocks))
    }

    @Test
    fun `isInInlinedBlock returns false on empty blocks`() {
        val detector = InlinedFinallyDetector()
        assertFalse(detector.isInInlinedBlock(10, emptyList()))
    }

    @Test
    fun `isInInlinedBlock returns true for line in any block`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
                InlinedFinallyDetector.InlinedFinallyBlock("bar", 100, 200, 150, listOf(100, 150, 200)),
            )
        assertTrue(detector.isInInlinedBlock(10, blocks))
        assertTrue(detector.isInInlinedBlock(150, blocks))
    }

    @Test
    fun `countSkippedMutations counts mutations within blocks`() {
        val detector = InlinedFinallyDetector()
        val blocks = listOf(
            InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
        )
        val mutations = listOf(
            // in block
            createMutation(10),
            createMutation(15),
            createMutation(20),
            // not in block
            createMutation(5),
            createMutation(50),
        )
        assertEquals(3, detector.countSkippedMutations(mutations, blocks))
    }

    @Test
    fun `countSkippedMutations on mutations not in any block returns 0`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
            )
        val mutations =
            listOf(
                createMutation(5), // not in block
                createMutation(50), // not in block
            )
        assertEquals(0, detector.countSkippedMutations(mutations, blocks))
    }

    @Test
    fun `countSkippedMutations counts mutations within blocks`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
            )
        val mutations =
            listOf(
                createMutation(10), // in block
                createMutation(15), // in block
                createMutation(20), // in block
                createMutation(5), // not in block
                createMutation(50), // not in block
            )
        assertEquals(3, detector.countSkippedMutations(mutations, blocks))
    }

    @Test
    fun `countSkippedMutations on empty blocks returns 0`() {
        val detector = InlinedFinallyDetector()
        val mutations =
            listOf(
                createMutation(10),
                createMutation(20),
            )
        assertEquals(0, detector.countSkippedMutations(mutations, emptyList()))
    }

    @Test
    fun `countSkippedMutations counts all mutations in multiple blocks`() {
        val detector = InlinedFinallyDetector()
        val blocks =
            listOf(
                InlinedFinallyDetector.InlinedFinallyBlock("foo", 10, 20, 15, listOf(5, 10, 15)),
                InlinedFinallyDetector.InlinedFinallyBlock("bar", 100, 200, 150, listOf(100, 150, 200)),
            )
        val mutations =
            listOf(
                createMutation(15),
                createMutation(150),
            )
        assertEquals(2, detector.countSkippedMutations(mutations, blocks))
    }

    private fun createMutation(line: Int): MutationInfo {
        return MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = "com.Foo",
            methodName = "method",
            methodDescriptor = "()V",
            lineNumber = line,
            description = "test",
            originalOpcode = 96,
            mutatedOpcode = 100,
        )
    }

    private fun buildEmptyClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNoMethods(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        // No methods defined
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithSimpleMethod(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
        mv?.visitCode()
        val lineLabel = Label()
        mv?.visitLineNumber(10, lineLabel)
        mv?.visitLabel(lineLabel)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithSimpleFinally(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "withFinally", "()V", null, null)
        mv?.visitCode()
        val startLabel = Label()
        mv?.visitLabel(startLabel)
        val handlerLabel = Label()
        mv?.visitLabel(handlerLabel)
        // try block: empty
        val endLabel = Label()
        mv?.visitLabel(endLabel)
        // No catch (finally is null type)
        mv?.visitTryCatchBlock(startLabel, endLabel, handlerLabel, null)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
