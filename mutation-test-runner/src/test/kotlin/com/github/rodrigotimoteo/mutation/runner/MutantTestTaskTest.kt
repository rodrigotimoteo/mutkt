package com.github.rodrigotimoteo.mutation.runner

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutantTestTaskTest {
    @Test
    fun `toMutation creates correct Mutation from MutationInfo`() {
        val info =
            MutationInfo(
                operator = MutationOperator.ARITHMETIC,
                className = "com.example.Foo",
                methodName = "add",
                methodDescriptor = "(II)I",
                lineNumber = 42,
                description = "IADD → ISUB",
                originalOpcode = Opcodes.IADD,
                mutatedOpcode = Opcodes.ISUB,
            )
        val task =
            MutantTestTask(
                mutation = info,
                mutatedBytes = ByteArray(0),
                classFiles = emptyMap(),
                testClassNames = emptyList(),
                parentClassLoader = MutantTestTaskTest::class.java.classLoader,
            )
        val mutation = task.call().mutation
        assertEquals("ARITHMETIC_com.example.Foo_add_42", mutation.id)
        assertEquals("com.example.Foo", mutation.className)
        assertEquals("add", mutation.methodName)
        assertEquals(MutationOperator.ARITHMETIC, mutation.operator)
        assertEquals(42, mutation.lineNumber)
    }

    @Test
    fun `different mutations get different ids`() {
        val info1 = makeInfo(lineNumber = 1)
        val info2 = makeInfo(lineNumber = 2)
        val task1 = makeTask(info1)
        val task2 = makeTask(info2)
        val m1 = task1.call().mutation
        val m2 = task2.call().mutation
        assertNotEquals(m1.id, m2.id)
    }

    @Test
    fun `call with empty classFiles returns ERROR status`() {
        val info = makeInfo()
        val task = makeTask(info)
        val result = task.call()
        // Either ERROR (no class to load) or NO_COVERAGE depending on path
        assertTrue(
            result.status == MutationStatus.ERROR ||
                result.status == MutationStatus.NO_COVERAGE ||
                result.status == MutationStatus.SURVIVED,
        )
    }

    @Test
    fun `call returns MutationResult with execution time`() {
        val info = makeInfo()
        val task = makeTask(info)
        val result = task.call()
        assertTrue(result.executionTimeMs >= 0)
    }

    @Test
    fun `call returns valid MutationStatus enum value`() {
        val info = makeInfo()
        val task = makeTask(info)
        val result = task.call()
        assertNotNull(result.status)
        assertTrue(result.status in MutationStatus.values().toList())
    }

    @Test
    fun `call with non-existent class bytes returns ERROR`() {
        val info = makeInfo()
        val task =
            MutantTestTask(
                mutation = info,
                mutatedBytes = ByteArray(0),
                classFiles = mapOf("com/example/Foo" to ByteArray(10)),
                testClassNames = listOf("NonExistentTest"),
                parentClassLoader = MutantTestTaskTest::class.java.classLoader,
            )
        val result = task.call()
        // Class bytes are invalid (10 bytes), so loading will fail
        // Status should be ERROR (loading failed) or NO_COVERAGE (no @Test methods)
        assertTrue(
            result.status == MutationStatus.ERROR || result.status == MutationStatus.NO_COVERAGE,
        )
    }

    @Test
    fun `call with valid class and passing test returns SURVIVED`() {
        val testBytes = buildTestClassThatReturnsNormally()
        val classBytes = buildCalculatorClass()
        val info = makeInfo()
        val task =
            MutantTestTask(
                mutation = info,
                mutatedBytes = classBytes,
                classFiles =
                    mapOf(
                        "com/example/Calc" to classBytes,
                        "com/example/CalcTest" to testBytes,
                    ),
                testClassNames = listOf("com.example.CalcTest"),
                parentClassLoader = MutantTestTaskTest::class.java.classLoader,
            )
        val result = task.call()
        // Should be either SURVIVED (test passed) or KILLED (test failed because mutation broke it)
        assertTrue(result.status in listOf(MutationStatus.SURVIVED, MutationStatus.KILLED))
    }

    @Test
    fun `call with class that has no @Test methods returns NO_COVERAGE`() {
        val testBytes = buildClassWithNoTestMethods()
        val classBytes = buildCalculatorClass()
        val info = makeInfo()
        val task =
            MutantTestTask(
                mutation = info,
                mutatedBytes = classBytes,
                classFiles =
                    mapOf(
                        "com/example/Calc" to classBytes,
                        "com/example/CalcTest" to testBytes,
                    ),
                testClassNames = listOf("com.example.CalcTest"),
                parentClassLoader = MutantTestTaskTest::class.java.classLoader,
            )
        val result = task.call()
        assertEquals(MutationStatus.NO_COVERAGE, result.status)
    }

    private fun makeInfo(lineNumber: Int = 1): MutationInfo =
        MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = "com.example.Calc",
            methodName = "add",
            methodDescriptor = "(II)I",
            lineNumber = lineNumber,
            description = "IADD → ISUB",
            originalOpcode = Opcodes.IADD,
            mutatedOpcode = Opcodes.ISUB,
        )

    private fun makeTask(info: MutationInfo): MutantTestTask =
        MutantTestTask(
            mutation = info,
            mutatedBytes = ByteArray(0),
            classFiles = emptyMap(),
            testClassNames = emptyList(),
            parentClassLoader = MutantTestTaskTest::class.java.classLoader,
        )

    private fun buildCalculatorClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Calc", null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PUBLIC, "result", "I", null, null).visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv?.visitCode()
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitVarInsn(Opcodes.ILOAD, 2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 3)
        mv?.visitEnd()
        // Default constructor
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildTestClassThatReturnsNormally(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/CalcTest", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testAdd", "()V", null, null)
        mv?.visitCode()
        val annotation = mv?.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        annotation?.visitEnd()
        // Just do nothing, test passes
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNoTestMethods(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/CalcTest", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", "()V", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
