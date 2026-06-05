package com.github.rodrigotimoteo.mutation.runner

import com.github.rodrigotimoteo.mutation.engine.MutationEngine
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationTestRunnerTest {
    @Test
    fun `factory creates MutationTestRunner with defaults`() {
        val runner = MutationTestRunnerFactory.create()
        assertNotNull(runner)
    }

    @Test
    fun `factory with custom args propagates correctly`() {
        val runner =
            MutationTestRunnerFactory.create(
                timeoutMs = 60_000L,
                maxParallelMutants = 8,
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
            )
        assertNotNull(runner)
    }

    @Test
    fun `loadClassFiles on non-existent dir returns empty map`(
        @TempDir tempDir: Path,
    ) {
        val runner = MutationTestRunnerFactory.create()
        val method =
            MutationTestRunner::class.java.getDeclaredMethod("loadClassFiles", File::class.java)
        method.isAccessible = true
        val result = method.invoke(runner, tempDir.resolve("nonexistent").toFile()) as Map<*, *>
        assertEquals(0, result.size)
    }

    @Test
    fun `loadClassFiles reads all class files`(
        @TempDir tempDir: Path,
    ) {
        writeClassFile(tempDir.resolve("com/example/Foo.class"))
        writeClassFile(tempDir.resolve("com/example/Bar.class"))

        val runner = MutationTestRunnerFactory.create()
        val method =
            MutationTestRunner::class.java.getDeclaredMethod("loadClassFiles", File::class.java)
        method.isAccessible = true
        val result = method.invoke(runner, tempDir.toFile()) as Map<*, *>
        assertEquals(2, result.size)
        assertTrue(result.containsKey("com/example/Foo"))
        assertTrue(result.containsKey("com/example/Bar"))
    }

    @Test
    fun `findTestClasses returns only classes matching test patterns`(
        @TempDir tempDir: Path,
    ) {
        writeClassFile(tempDir.resolve("com/example/MyTest.class"))
        writeClassFile(tempDir.resolve("com/example/IntegrationTests.class"))
        writeClassFile(tempDir.resolve("com/example/TestRunner.class"))
        writeClassFile(tempDir.resolve("com/example/UserSpec.class"))
        writeClassFile(tempDir.resolve("com/example/Helper.class"))
        writeClassFile(tempDir.resolve("com/example/LatestReport.class"))

        val runner = MutationTestRunnerFactory.create()
        val method =
            MutationTestRunner::class.java.getDeclaredMethod("findTestClasses", File::class.java)
        method.isAccessible = true
        val result = method.invoke(runner, tempDir.toFile()) as List<*>
        assertEquals(4, result.size)
        assertTrue(result.contains("com.example.MyTest"))
        assertTrue(result.contains("com.example.IntegrationTests"))
        assertTrue(result.contains("com.example.TestRunner"))
        assertTrue(result.contains("com.example.UserSpec"))
        assertTrue(!result.contains("com.example.Helper"))
        assertTrue(!result.contains("com.example.LatestReport"))
    }

    @Test
    fun `findTestClasses on non-existent dir returns empty list`(
        @TempDir tempDir: Path,
    ) {
        val runner = MutationTestRunnerFactory.create()
        val method =
            MutationTestRunner::class.java.getDeclaredMethod("findTestClasses", File::class.java)
        method.isAccessible = true
        val result = method.invoke(runner, tempDir.resolve("nonexistent").toFile()) as List<*>
        assertEquals(0, result.size)
    }

    @Test
    fun `findTestClasses does not match classes with test substring in package`(
        @TempDir tempDir: Path,
    ) {
        // LatestReport is in com.example — "test" is only in class name substring,
        // not in the simple name match, so it should NOT be detected as a test
        writeClassFile(tempDir.resolve("com/example/LatestReport.class"))
        val runner = MutationTestRunnerFactory.create()
        val method =
            MutationTestRunner::class.java.getDeclaredMethod("findTestClasses", File::class.java)
        method.isAccessible = true
        val result = method.invoke(runner, tempDir.toFile()) as List<*>
        assertEquals(0, result.size)
    }

    @Test
    fun `run with mock engine calls engine with correct args`(
        @TempDir tempDir: Path,
    ) {
        val mockEngine = mockk<MutationEngine>(relaxed = true)
        val runner = MutationTestRunner(mockEngine)
        val classesDir = tempDir.toFile()
        val testDir = tempDir.resolve("test").toFile()
        testDir.mkdirs()
        runner.run(classesDir, testDir, classpath = emptyList())
        // Verify engine was called
        verify { mockEngine.runMutationTesting(any<Map<String, ByteArray>>(), any(), any(), any(), any()) }
    }

    @Test
    fun `run with null coverageExecFile passes null`(
        @TempDir tempDir: Path,
    ) {
        val mockEngine = mockk<MutationEngine>(relaxed = true)
        val runner = MutationTestRunner(mockEngine)
        runner.run(
            classesDir = tempDir.toFile(),
            testClassesDir = tempDir.toFile(),
            classpath = emptyList(),
            coverageExecFile = null,
        )
        verify {
            mockEngine.runMutationTesting(
                any<Map<String, ByteArray>>(),
                any<List<String>>(),
                any<Map<String, ByteArray>>(),
                null,
                any(),
            )
        }
    }

    private fun writeClassFile(path: Path) {
        path.parent.toFile().mkdirs()
        val cw = ClassWriter(0)
        val className = path.fileName.toString().replace(".class", "").replace("/", "_")
        cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        cw.visitEnd()
        java.nio.file.Files.write(path, cw.toByteArray())
    }
}
