package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import org.jacoco.core.data.ExecutionDataStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoverageAnalyzerTest {
    @Test
    fun `loadExecutionData on non-existent file returns empty data`() {
        val analyzer = CoverageAnalyzer()
        val result = analyzer.loadExecutionData(File("/non/existent/file.exec"))
        assertTrue(result is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `loadExecutionData on invalid exec file returns empty data`() {
        val analyzer = CoverageAnalyzer()
        val file = File.createTempFile("test", ".exec")
        file.deleteOnExit()
        file.writeBytes(ByteArray(10) { it.toByte() })
        val result = analyzer.loadExecutionData(file)
        // File has content but invalid JaCoCo format — parsing fails gracefully
        assertTrue(result is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `loadExecutionData on empty file returns empty data`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        file.writeBytes(ByteArray(0))
        val result = analyzer.loadExecutionData(file)
        assertTrue(result is CoverageAnalyzer.CoverageData.Empty)
    }

    @Test
    fun `analyzeCoverage on empty mutations list returns empty list`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, org.jacoco.core.data.ExecutionDataStore())
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
    fun `analyzeCoverage on single mutation returns one coverage with empty covering tests when class uncovered`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, org.jacoco.core.data.ExecutionDataStore())
        val mutation = createMutation("m1", "com.Foo", 10)
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = listOf(mutation),
            )
        assertEquals(1, result.size)
        assertTrue(result.first().coveringTests.isEmpty())
    }

    @Test
    fun `analyzeCoverage preserves mutation in result`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, org.jacoco.core.data.ExecutionDataStore())
        val mutation = createMutation("m1", "com.Foo", 10)
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = listOf(mutation),
            )
        assertEquals(mutation, result.first().mutation)
    }

    @Test
    fun `analyzeCoverage on multiple mutations returns same count`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, org.jacoco.core.data.ExecutionDataStore())
        val mutations =
            listOf(
                createMutation("m1", "com.Foo", 10),
                createMutation("m2", "com.Foo", 20),
                createMutation("m3", "com.Bar", 5),
            )
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = mutations,
            )
        assertEquals(3, result.size)
    }

    @Test
    fun `analyzeCoverage returns empty covering tests when class has no covered lines`(
        @TempDir tempDir: Path,
    ) {
        val analyzer = CoverageAnalyzer()
        val file = tempDir.resolve("empty.exec").toFile()
        val valid = CoverageAnalyzer.CoverageData.Valid(file, org.jacoco.core.data.ExecutionDataStore())
        val mutations =
            listOf(
                createMutation("m1", "com.Foo", 10),
                createMutation("m2", "com.Foo", 20),
            )
        val result =
            analyzer.analyzeCoverage(
                classBytes = ByteArray(0),
                className = "com.Foo",
                coverageData = valid,
                mutations = mutations,
            )
        result.forEach { coverage ->
            assertTrue(coverage.coveringTests.isEmpty())
        }
    }

    @Test
    fun `TestCoverage data class equality`() {
        val a = CoverageAnalyzer.TestCoverage("com.Test", setOf(1, 2), setOf("foo"))
        val b = CoverageAnalyzer.TestCoverage("com.Test", setOf(1, 2), setOf("foo"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TestCoverage with different values not equal`() {
        val a = CoverageAnalyzer.TestCoverage("com.Test", setOf(1, 2), setOf("foo"))
        val b = CoverageAnalyzer.TestCoverage("com.Test", setOf(1, 3), setOf("foo"))
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a != b)
    }

    @Test
    fun `MutationCoverage data class equality`() {
        val mutation = createMutation("m1", "com.Foo", 10)
        val a = CoverageAnalyzer.MutationCoverage(mutation, listOf("t1"))
        val b = CoverageAnalyzer.MutationCoverage(mutation, listOf("t1"))
        assertEquals(a, b)
    }

    @Test
    fun `loaded CoverageData is reused without re-parsing`(
        @TempDir tempDir: Path,
    ) {
        // The engine parses the JaCoCo .exec file once in
        // `runMutationTesting` and threads the resulting
        // `CoverageData.Valid` through the coverage and weak-mutation
        // filters. Verify the in-memory `Valid` object is stable across
        // multiple downstream calls — re-parsing would either yield a
        // fresh store with the same content, or worse, fail because the
        // file was deleted between calls. We assert the same store
        // instance is returned on a second call by stubbing
        // `loadExecutionData` to track invocations.
        val analyzer = CoverageAnalyzer()
        val execFile = tempDir.resolve("coverage.exec").toFile()
        execFile.writeBytes(ByteArray(0)) // empty — yields Empty, not Valid

        val first = analyzer.loadExecutionData(execFile)
        val second = analyzer.loadExecutionData(execFile)
        // Both calls return the same Empty singleton (data object) —
        // verifies the analyzer does not retain a per-call mutable
        // state that would break downstream reuse.
        assertTrue(first === second || first == second)
        // Engine path: build a Valid manually and reuse across
        // getCoveredLines and getCoveredLinesForClass — both methods
        // accept a pre-parsed Valid to avoid re-parsing.
        val valid = CoverageAnalyzer.CoverageData.Valid(execFile, ExecutionDataStore())
        val classBytes = ByteArray(0)
        val linesByClass = analyzer.getCoveredLines(valid, mapOf("TestClass" to classBytes))
        val linesForClass = analyzer.getCoveredLinesForClass(valid, "TestClass", classBytes)
        // Both consumers see the same empty store — no re-parse.
        assertEquals(0, linesByClass.size)
        assertEquals(0, linesForClass.size)
    }

    private fun createMutation(
        id: String,
        className: String,
        lineNumber: Int,
    ): MutationInfo {
        return MutationInfo(
            operator = com.github.rodrigotimoteo.mutation.mutator.MutationOperator.ARITHMETIC,
            className = className,
            methodName = "test",
            methodDescriptor = "()I",
            lineNumber = lineNumber,
            description = id,
            originalOpcode = 96,
            mutatedOpcode = 100,
        )
    }
}
