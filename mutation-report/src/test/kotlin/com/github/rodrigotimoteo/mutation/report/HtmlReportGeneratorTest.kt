package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HtmlReportGeneratorTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var generator: HtmlReportGenerator

    @BeforeEach
    fun setUp() {
        generator = HtmlReportGenerator()
    }

    @Test
    fun `generate creates HTML file`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)

        assertTrue(outputFile.exists(), "expected HTML file at ${outputFile.absolutePath}")
        assertTrue(outputFile.name.endsWith(".html"), "expected .html extension, got: ${outputFile.name}")
    }

    @Test
    fun `generate creates valid HTML`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.startsWith("<!DOCTYPE html>"), "expected DOCTYPE at start, got: ${content.take(50)}")
        assertTrue(content.contains("<html lang=\"en\">"), "expected <html lang=\"en\"> tag")
        assertTrue(content.contains("</html>"), "expected closing </html> tag")
    }

    @Test
    fun `generate includes summary statistics`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.contains("Total Mutations"))
        assertTrue(content.contains("Killed"))
        assertTrue(content.contains("Survived"))
        assertTrue(content.contains("No Coverage"))
    }

    @Test
    fun `generate includes kill rate bar`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.contains("Kill Rate:"))
        assertTrue(content.contains("bar-fill"))
    }

    @Test
    fun `generate includes per-class breakdown`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.contains("Per-Class Breakdown"))
        assertTrue(content.contains("com.example.Foo"))
    }

    @Test
    fun `generate includes detailed results table`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.contains("Detailed Results"))
        assertTrue(content.contains("<table>"))
        assertTrue(content.contains("ARITHMETIC"))
    }

    @Test
    fun `generate includes CSS styles`() {
        val report = createTestReport()
        val outputFile = generator.generate(report, tempDir)
        val content = outputFile.readText()

        assertTrue(content.contains("<style>"))
        assertTrue(content.contains("</style>"))
        assertTrue(content.contains(".stat-card"))
    }

    @Test
    fun `generate handles empty report`() {
        val report =
            MutationReport(
                results = emptyList(),
                totalMutations = 0,
                killedMutations = 0,
                survivedMutations = 0,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 0,
            )

        val outputFile = generator.generate(report, tempDir)
        assertTrue(outputFile.exists(), "expected HTML file for empty report")

        val content = outputFile.readText()
        assertTrue(content.contains("Total Mutations"), "expected 'Total Mutations' label")
        assertTrue(content.contains("0"), "expected '0' for empty totals")
    }

    @Test
    fun `generate creates output directory if needed`() {
        val outputDir = File(tempDir, "nested/output")
        val report = createTestReport()

        val outputFile = generator.generate(report, outputDir)
        assertTrue(outputFile.exists(), "expected file in nested dir, got: ${outputFile.absolutePath}")
        assertTrue(outputDir.exists(), "expected nested directory created")
    }

    private fun createTestReport(): MutationReport {
        val results = mutableListOf<MutationResult>()

        // 7 killed in Foo
        for (i in 1..7) {
            results.add(
                MutationResult(
                    mutation = createMutation("com.example.Foo", "bar$i", i * 10),
                    status = MutationStatus.KILLED,
                    executionTimeMs = 100,
                ),
            )
        }

        // 2 survived in Foo
        for (i in 1..2) {
            results.add(
                MutationResult(
                    mutation = createMutation("com.example.Foo", "baz$i", i * 20),
                    status = MutationStatus.SURVIVED,
                    executionTimeMs = 100,
                ),
            )
        }

        // 1 no coverage in Bar
        results.add(
            MutationResult(
                mutation = createMutation("com.example.Bar", "method", 50),
                status = MutationStatus.NO_COVERAGE,
                executionTimeMs = 0,
            ),
        )

        return MutationReport(
            results = results,
            totalMutations = 10,
            killedMutations = 7,
            survivedMutations = 2,
            errorMutations = 0,
            timeoutMutations = 0,
            noCoverageMutations = 1,
            totalExecutionTimeMs = 1000,
        )
    }

    private fun createMutation(
        className: String,
        methodName: String,
        lineNumber: Int,
    ): Mutation {
        return Mutation(
            id = "ARITHMETIC::$className::$methodName::$lineNumber",
            className = className,
            methodName = methodName,
            methodDescriptor = "()V",
            operator = MutationOperator.ARITHMETIC,
            lineNumber = lineNumber,
            originalBytecode = ByteArray(10),
            mutatedBytecode = ByteArray(10),
            description = "Test mutation",
        )
    }
}
