package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.ExecutionDataWriter
import org.jacoco.core.data.SessionInfo
import org.jacoco.core.data.SessionInfoStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoverageAnalyzerFullTest {
    @Test
    fun `loadExecutionData with non-existent file returns empty data`() {
        val analyzer = CoverageAnalyzer()
        val result = analyzer.loadExecutionData(File("/non/existent/file.exec"))
        assertTrue(result is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `analyzeCoverage with multiple mutations and mixed coverage`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = io.mockk.spyk(CoverageAnalyzer())
        io.mockk.every {
            analyzer.getCoveredLinesForClass(any(), any(), any())
        } returns setOf(10, 11)

        val execFile = tempDir.resolve("coverage.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val className = "TestClass"
        val coverageData = assertIs<CoverageAnalyzer.CoverageData.Valid>(analyzer.loadExecutionData(execFile))

        val mutations =
            listOf(
                createMutation("m1", className, 10),
                createMutation("m2", className, 11),
                createMutation("m3", className, 99),
            )

        val result =
            analyzer.analyzeCoverage(
                classBytes = classBytes,
                className = className,
                coverageData = coverageData,
                mutations = mutations,
            )

        assertEquals(3, result.size)
        assertEquals(listOf("covered"), result[0].coveringTests)
        assertEquals(listOf("covered"), result[1].coveringTests)
        assertEquals(emptyList(), result[2].coveringTests)

        io.mockk.unmockkAll()
    }

    @Test
    fun `loadExecutionData with invalid bytes returns empty data`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("invalid.exec").toFile()
        file.writeBytes(ByteArray(20) { (it * 7 + 13).toByte() })
        val result = analyzer.loadExecutionData(file)
        assertTrue(result is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `loadExecutionData with valid empty exec file returns non-empty data`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(file)
        val result = analyzer.loadExecutionData(file)
        assertTrue(result is CoverageAnalyzer.CoverageData.Valid)
        val valid = result
        assertEquals(file, valid.execFile)
        assertNotNull(valid.executionDataStore)
    }

    @Test
    fun `getCoveredLines with non-existent exec file returns empty map`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val classBytes = buildClassWithLineNumbers()
        val classFiles = mapOf("TestClass" to classBytes)
        val result = analyzer.getCoveredLines(File("/non/existent.exec"), classFiles)
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLines with empty exec file returns empty map`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("empty.exec").toFile()
        execFile.writeBytes(ByteArray(0))
        val classBytes = buildClassWithLineNumbers()
        val classFiles = mapOf("TestClass" to classBytes)
        val result = analyzer.getCoveredLines(execFile, classFiles)
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLines with valid exec file but no matching data returns empty map`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val classFiles = mapOf("TestClass" to classBytes)
        val result = analyzer.getCoveredLines(execFile, classFiles)
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLinesForClass with non-existent exec file returns empty set`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val classBytes = buildClassWithLineNumbers()
        val result =
            analyzer.getCoveredLinesForClass(
                File("/non/existent.exec"),
                "TestClass",
                classBytes,
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLinesForClass with empty exec file returns empty set`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("empty.exec").toFile()
        execFile.writeBytes(ByteArray(0))
        val classBytes = buildClassWithLineNumbers()
        val result = analyzer.getCoveredLinesForClass(execFile, "TestClass", classBytes)
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLinesForClass with valid exec file but no matching data returns empty set`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val result = analyzer.getCoveredLinesForClass(execFile, "TestClass", classBytes)
        assertEquals(0, result.size)
    }

    @Test
    fun `analyzeCoverage with empty coverage data returns covered for each mutation`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, ExecutionDataStore())
        val mutation = createMutation("m1", "com.Foo", 10)
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = listOf(mutation),
            )
        assertEquals(1, result.size)
        assertEquals(listOf("covered"), result.first().coveringTests)
    }

    @Test
    fun `analyzeCoverage with valid empty exec file returns covered for each mutation`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val coverageData = assertIs<CoverageAnalyzer.CoverageData.Valid>(analyzer.loadExecutionData(execFile))
        val mutation = createMutation("m1", "TestClass", 10)
        val result =
            analyzer.analyzeCoverage(
                classBytes = classBytes,
                className = "TestClass",
                coverageData = coverageData,
                mutations = listOf(mutation),
            )
        assertEquals(1, result.size)
        assertEquals(listOf("covered"), result.first().coveringTests)
    }

    @Test
    fun `analyzeCoverage with empty mutations list returns empty list`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, ExecutionDataStore())
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = emptyList(),
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `CoverageData Empty is a singleton`() {
        val a: CoverageAnalyzer.CoverageData = CoverageAnalyzer.CoverageData.Empty
        val b: CoverageAnalyzer.CoverageData = CoverageAnalyzer.CoverageData.Empty
        assertEquals(a, b)
        assertTrue(a is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `CoverageData Valid exposes execFile`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("test.exec").toFile()
        val data = CoverageAnalyzer.CoverageData.Valid(file, ExecutionDataStore())
        assertEquals(file, data.execFile)
        assertNotNull(data.executionDataStore)
    }

    @Test
    fun `CoverageData Valid holds all fields`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("test.exec").toFile()
        val store = ExecutionDataStore()
        val data = CoverageAnalyzer.CoverageData.Valid(file, store)
        assertEquals(file, data.execFile)
        assertEquals(store, data.executionDataStore)
    }

    @Test
    fun `CoverageData Valid equals and hashCode work correctly`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("test.exec").toFile()
        val store = ExecutionDataStore()
        val a = CoverageAnalyzer.CoverageData.Valid(file, store)
        val b = CoverageAnalyzer.CoverageData.Valid(file, store)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CoverageData Valid copy creates independent instance`(
        @TempDir tempDir: Path,
    ) {
        val file1 = tempDir.resolve("test1.exec").toFile()
        val file2 = tempDir.resolve("test2.exec").toFile()
        val original = CoverageAnalyzer.CoverageData.Valid(file1, ExecutionDataStore())
        val copy = original.copy(execFile = file2)
        assertEquals(file1, original.execFile)
        assertEquals(file2, copy.execFile)
    }

    @Test
    fun `analyzeCoverage filters covered vs uncovered mutations by line number`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = io.mockk.spyk(CoverageAnalyzer())
        io.mockk.every {
            analyzer.getCoveredLinesForClass(any(), any(), any())
        } returns setOf(10, 11)

        val execFile = tempDir.resolve("coverage.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val className = "TestClass"
        val coverageData = assertIs<CoverageAnalyzer.CoverageData.Valid>(analyzer.loadExecutionData(execFile))

        val coveredMutation = createMutation("m1", className, 10)
        val uncoveredMutation = createMutation("m2", className, 99)
        val mutations = listOf(coveredMutation, uncoveredMutation)

        val result =
            analyzer.analyzeCoverage(
                classBytes = classBytes,
                className = className,
                coverageData = coverageData,
                mutations = mutations,
            )

        assertEquals(2, result.size)
        val covered = result.find { it.mutation == coveredMutation }
        val uncovered = result.find { it.mutation == uncoveredMutation }
        assertNotNull(covered)
        assertNotNull(uncovered)
        assertEquals(listOf("covered"), covered.coveringTests)
        assertEquals(emptyList(), uncovered.coveringTests)

        io.mockk.unmockkAll()
    }

    @Test
    fun `findExecFiles finds jacoco test exec file`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val jacocoDir = tempDir.resolve("jacoco").toFile().apply { mkdirs() }
        val execFile = File(jacocoDir, "test.exec").apply { writeBytes(ByteArray(0)) }
        val result = analyzer.findExecFiles(tempDir.toFile())
        assertEquals(1, result.size)
        assertEquals(execFile.absolutePath, result.first().absolutePath)
    }

    @Test
    fun `findExecFiles finds multiple exec files`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val jacocoDir = tempDir.resolve("jacoco").toFile().apply { mkdirs() }
        val reportsDir = tempDir.resolve("reports/jacoco/test").toFile().apply { mkdirs() }
        File(jacocoDir, "test.exec").apply { writeBytes(ByteArray(0)) }
        File(reportsDir, "jacocoTestReport.exec").apply { writeBytes(ByteArray(0)) }
        val result = analyzer.findExecFiles(tempDir.toFile())
        assertEquals(2, result.size)
    }

    @Test
    fun `TestCoverage data class holds values correctly`() {
        val coverage =
            CoverageAnalyzer.TestCoverage(
                testClassName = "com.Test",
                coveredLines = setOf(1, 2, 3),
                coveredMethods = setOf("foo", "bar"),
            )
        assertEquals("com.Test", coverage.testClassName)
        assertEquals(setOf(1, 2, 3), coverage.coveredLines)
        assertEquals(setOf("foo", "bar"), coverage.coveredMethods)
    }

    @Test
    fun `MutationCoverage data class holds values correctly`() {
        val mutation = createMutation("m1", "com.Foo", 10)
        val coverage =
            CoverageAnalyzer.MutationCoverage(
                mutation = mutation,
                coveringTests = listOf("t1", "t2"),
            )
        assertEquals(mutation, coverage.mutation)
        assertEquals(listOf("t1", "t2"), coverage.coveringTests)
    }

    @Test
    fun `concurrent loadExecutionData calls are safe`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val latch = CountDownLatch(10)
        val executor = Executors.newFixedThreadPool(4)
        val results = mutableListOf<CoverageAnalyzer.CoverageData>()
        val lock = Object()

        repeat(10) {
            executor.submit {
                val result = analyzer.loadExecutionData(execFile)
                synchronized(lock) {
                    results.add(result)
                }
                latch.countDown()
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent loadExecutionData timed out")
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor failed to terminate")

        assertEquals(10, results.size)
        results.forEach {
            assertTrue(it is CoverageAnalyzer.CoverageData.Valid)
        }
    }

    @Test
    fun `concurrent getCoveredLinesForClass calls are safe`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val classBytes = buildClassWithLineNumbers()
        val latch = CountDownLatch(10)
        val executor = Executors.newFixedThreadPool(4)
        val results = mutableListOf<Set<Int>>()
        val lock = Object()

        repeat(10) {
            executor.submit {
                val result = analyzer.getCoveredLinesForClass(execFile, "TestClass", classBytes)
                synchronized(lock) {
                    results.add(result)
                }
                latch.countDown()
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent getCoveredLinesForClass timed out")
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor failed to terminate")

        assertEquals(10, results.size)
        results.forEach {
            assertEquals(0, it.size)
        }
    }

    @Test
    fun `getCoveredLines handles exception in analyzer gracefully`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val invalidClassBytes = ByteArray(10) { it.toByte() }
        val classFiles = mapOf("BadClass" to invalidClassBytes)
        val result = analyzer.getCoveredLines(execFile, classFiles)
        assertEquals(0, result.size)
    }

    @Test
    fun `getCoveredLinesForClass handles exception in analyzer gracefully`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("valid.exec").toFile()
        createEmptyExecFile(execFile)
        val invalidClassBytes = ByteArray(10) { it.toByte() }
        val result = analyzer.getCoveredLinesForClass(execFile, "BadClass", invalidClassBytes)
        assertEquals(0, result.size)
    }

    private fun createEmptyExecFile(file: File) {
        val sessionStore = SessionInfoStore()
        val executionStore = ExecutionDataStore()
        FileOutputStream(file).use { out ->
            val writer = ExecutionDataWriter(out)
            sessionStore.accept(writer)
            executionStore.accept(writer)
        }
    }

    private fun createExecFileWithCoverage(
        file: File,
        className: String,
        classBytes: ByteArray,
    ) {
        val classId = computeClassId(classBytes)
        val sessionStore = SessionInfoStore()
        sessionStore.visitSessionInfo(
            SessionInfo(
                "test",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
            ),
        )
        val executionStore = ExecutionDataStore()
        val probes = booleanArrayOf(true, true, true, true, true)
        executionStore.put(ExecutionData(classId, className, probes))

        FileOutputStream(file).use { out ->
            val writer = ExecutionDataWriter(out)
            sessionStore.accept(writer)
            executionStore.accept(writer)
        }
    }

    private fun computeClassId(classBytes: ByteArray): Long {
        val crcClass = Class.forName("org.jacoco.core.internal.data.CRC64")
        val method = crcClass.getDeclaredMethod("classId", ByteArray::class.java)
        return method.invoke(null, classBytes) as Long
    }

    private fun buildClassWithLineNumbers(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            Opcodes.ASM9,
            Opcodes.ACC_PUBLIC,
            "TestClass",
            null,
            "java/lang/Object",
            null,
        )
        cw.visitSource("TestClass.java", null)

        val initMv: MethodVisitor =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null,
            )
        initMv.visitCode()
        val initLabel = Label()
        initMv.visitLineNumber(1, initLabel)
        initMv.visitVarInsn(Opcodes.ALOAD, 0)
        initMv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        initMv.visitInsn(Opcodes.RETURN)
        initMv.visitMaxs(1, 1)
        initMv.visitEnd()

        val mv: MethodVisitor =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "test",
                "()I",
                null,
                null,
            )
        mv.visitCode()
        val label10 = Label()
        mv.visitLineNumber(10, label10)
        mv.visitInsn(Opcodes.ICONST_1)
        val label11 = Label()
        mv.visitLineNumber(11, label11)
        mv.visitInsn(Opcodes.ICONST_2)
        val label12 = Label()
        mv.visitLineNumber(12, label12)
        mv.visitInsn(Opcodes.IADD)
        val label13 = Label()
        mv.visitLineNumber(13, label13)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(2, 1)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun createMutation(
        id: String,
        className: String,
        lineNumber: Int,
    ): MutationInfo {
        return MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = className,
            methodName = "test",
            methodDescriptor = "()I",
            lineNumber = lineNumber,
            description = id,
            originalOpcode = Opcodes.IADD,
            mutatedOpcode = Opcodes.ISUB,
        )
    }
}
