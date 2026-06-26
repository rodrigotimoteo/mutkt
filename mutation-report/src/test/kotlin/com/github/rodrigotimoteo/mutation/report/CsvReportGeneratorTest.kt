package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvReportGeneratorTest {
    @TempDir
    lateinit var tempDir: Path

    private fun createMutation(
        id: String,
        className: String = "com.Foo",
    ): Mutation {
        return Mutation(
            id = id,
            className = className,
            methodName = "testMethod",
            methodDescriptor = "()V",
            operator = MutationOperator.ARITHMETIC,
            lineNumber = 10,
            originalBytecode = ByteArray(0),
            mutatedBytecode = ByteArray(0),
            description = "Arithmetic mutation",
        )
    }

    private fun createResult(
        id: String,
        status: MutationStatus,
    ): MutationResult {
        return MutationResult(
            mutation = createMutation(id),
            status = status,
            executionTimeMs = 100,
        )
    }

    private fun createResult(
        id: String,
        status: MutationStatus,
        className: String,
    ): MutationResult {
        return MutationResult(
            mutation = createMutation(id, className),
            status = status,
            executionTimeMs = 100,
        )
    }

    private fun createReport(vararg results: MutationResult): MutationReport {
        val killed = results.count { it.status == MutationStatus.KILLED }
        val survived = results.count { it.status == MutationStatus.SURVIVED }
        val errors = results.count { it.status == MutationStatus.ERROR }
        val timeouts = results.count { it.status == MutationStatus.TIMEOUT }
        val noCoverage = results.count { it.status == MutationStatus.NO_COVERAGE }
        return MutationReport(
            results = results.toList(),
            totalMutations = results.size,
            killedMutations = killed,
            survivedMutations = survived,
            errorMutations = errors,
            timeoutMutations = timeouts,
            noCoverageMutations = noCoverage,
            totalExecutionTimeMs = results.sumOf { it.executionTimeMs },
        )
    }

    @Test
    fun `generate produces valid CSV with header and rows`() {
        val report = createReport(createResult("m1", MutationStatus.KILLED))
        val file = CsvReportGenerator.generate(report, tempDir.toFile())

        assertTrue(file.exists(), "expected mutations.csv at ${file.absolutePath}")
        assertEquals("mutations.csv", file.name)

        val content = file.readText()
        val lines = content.trim().split("\n")
        assertEquals(2, lines.size) // header + 1 data row
        assertTrue(lines[0].startsWith("mutation_id,status,operator"), "expected CSV header, got: ${lines[0]}")
    }

    @Test
    fun `generateEnhanced includes WEAK and STRONG column`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
            )
        val report = createReport(*results.toTypedArray())
        val subsumedBy = mapOf("m2" to "m1")
        val weakMutations = setOf("m2")

        val file = CsvReportGenerator.generateEnhanced(report, subsumedBy, weakMutations, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "STRONG")
        assertContains(content, "WEAK")
    }

    @Test
    fun `generateSummary produces per-class score summary`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, "com.Foo"),
                createResult("m2", MutationStatus.KILLED, "com.Foo"),
                createResult("m3", MutationStatus.SURVIVED, "com.Foo"),
                // 2/3 = 66%
                createResult("m4", MutationStatus.KILLED, "com.Bar"),
                // 1/1 = 100%
            )
        val report = createReport(*results.toTypedArray())

        val file = CsvReportGenerator.generateSummary(report, tempDir.toFile())

        assertTrue(file.exists(), "expected mutation-summary.csv at ${file.absolutePath}")
        assertEquals("mutation-summary.csv", file.name)

        val content = file.readText()
        val lines = content.trim().split("\n")
        assertEquals(3, lines.size) // header + 2 classes

        assertContains(content, "class,total_mutations,killed,survived,subsumed,score")
        assertContains(content, "com.Foo")
        assertContains(content, "com.Bar")
    }

    @Test
    fun `summary with multiple classes groups correctly`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, "com.A"),
                createResult("m2", MutationStatus.SURVIVED, "com.A"),
                createResult("m3", MutationStatus.KILLED, "com.B"),
                createResult("m4", MutationStatus.KILLED, "com.B"),
            )
        val report = createReport(*results.toTypedArray())

        val file = CsvReportGenerator.generateSummary(report, tempDir.toFile())
        val content = file.readText()

        // Parse CSV — assert column values, not exact string match
        val dataRows = content.trim().split("\n").drop(1).map { it.split(",") }
        val rowA = dataRows.single { it[0] == "com.A" }
        val rowB = dataRows.single { it[0] == "com.B" }
        // com.A: 1 killed, 1 survived → score 50
        assertEquals(listOf("com.A", "2", "1", "1", "0", "50"), rowA)
        // com.B: 2 killed, 0 survived → score 100
        assertEquals(listOf("com.B", "2", "2", "0", "0", "100"), rowB)
    }

    @Test
    fun `empty report produces header only`() {
        val report = createReport()
        val file = CsvReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText().trim()

        val lines = content.split("\n")
        assertEquals(1, lines.size) // header only
        assertTrue(lines[0].startsWith("mutation_id"), "expected CSV header, got: ${lines[0]}")
    }

    @Test
    fun `generateSummary with zero mutations produces header only`() {
        val report = createReport()
        val file = CsvReportGenerator.generateSummary(report, tempDir.toFile())
        val content = file.readText().trim()

        val lines = content.split("\n")
        assertEquals(1, lines.size)
        assertEquals("class,total_mutations,killed,survived,subsumed,score", lines[0])
    }

    @Test
    fun `generate returns correct file path`() {
        val report = createReport()
        val file = CsvReportGenerator.generate(report, tempDir.toFile())

        assertEquals(
            File(tempDir.toFile(), "mutations.csv").absolutePath,
            file.absolutePath,
        )
    }

    @Test
    fun `generateEnhanced returns correct file path`() {
        val report = createReport()
        val file = CsvReportGenerator.generateEnhanced(report, emptyMap(), emptySet(), tempDir.toFile())

        assertEquals(
            File(tempDir.toFile(), "mutations.csv").absolutePath,
            file.absolutePath,
        )
    }
}
