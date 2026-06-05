package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HtmlReportGeneratorTest {
    @TempDir
    lateinit var tempDir: Path

    private fun createMutation(
        id: String = "mut-1",
        className: String = "com.example.MyClass",
        methodName: String = "doSomething",
        operator: MutationOperator = MutationOperator.NEGATE_CONDITIONALS,
        lineNumber: Int = 42,
        description: String = "Negated conditional",
    ): Mutation =
        Mutation(
            id = id,
            className = className,
            methodName = methodName,
            methodDescriptor = "()V",
            operator = operator,
            lineNumber = lineNumber,
            originalBytecode = byteArrayOf(),
            mutatedBytecode = byteArrayOf(),
            description = description,
        )

    private fun createMutationResult(
        mutation: Mutation,
        status: MutationStatus = MutationStatus.KILLED,
        executionTimeMs: Long = 150,
        errorMessage: String? = null,
    ): MutationResult =
        MutationResult(
            mutation = mutation,
            status = status,
            executionTimeMs = executionTimeMs,
            errorMessage = errorMessage,
        )

    private fun createReport(results: List<MutationResult>): MutationReport {
        val killed = results.count { it.isKilled }
        val survived = results.count { it.isSurvived }
        val errors = results.count { it.status == MutationStatus.ERROR }
        val timeouts = results.count { it.status == MutationStatus.TIMEOUT }
        val noCoverage = results.count { it.status == MutationStatus.NO_COVERAGE }
        val totalTime = results.sumOf { it.executionTimeMs }
        return MutationReport(
            results = results,
            totalMutations = results.size,
            killedMutations = killed,
            survivedMutations = survived,
            errorMutations = errors,
            timeoutMutations = timeouts,
            noCoverageMutations = noCoverage,
            totalExecutionTimeMs = totalTime,
        )
    }

    @Test
    fun `generate produces valid HTML with DOCTYPE and charset`() {
        val report = createReport(emptyList())
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("<!DOCTYPE html>")
        assertThat(html).contains("<meta charset=\"UTF-8\">")
        assertThat(html).contains("<html lang=\"en\">")
        assertThat(html).contains("</html>")
    }

    @Test
    fun `generate includes mutation report title`() {
        val report = createReport(emptyList())
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("<title>Mutation Test Report</title>")
        assertThat(html).contains("<h1>Mutation Test Report</h1>")
    }

    @Test
    fun `generate includes summary statistics`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("Total")
        assertThat(html).contains("Killed")
        assertThat(html).contains("Survived")
        assertThat(html).contains("Errors")
        assertThat(html).contains("No Coverage")
        assertThat(html).contains("Score")
    }

    @Test
    fun `generate includes killed percentage in summary`() {
        val killed = createMutationResult(createMutation(id = "m1"), MutationStatus.KILLED)
        val survived = createMutationResult(createMutation(id = "m2"), MutationStatus.SURVIVED)
        val report = createReport(listOf(killed, survived))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("50%")
    }

    @Test
    fun `generate escapes html special chars in class names`() {
        val mutation = createMutation(className = "com.example.Foo<Bar>&Baz")
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).doesNotContain("com.example.Foo<Bar>&Baz")
        assertThat(html).contains("&lt;")
        assertThat(html).contains("&gt;")
        assertThat(html).contains("&amp;")
    }

    @Test
    fun `generate escapes double quote in class names`() {
        val mutation = createMutation(className = "com.example.Foo\"Bar")
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).doesNotContain("com.example.Foo\"Bar")
        assertThat(html).contains("&quot;")
    }

    @Test
    fun `generate includes table with mutation results`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("<table>")
        assertThat(html).contains("<th>ID</th>")
        assertThat(html).contains("<th>Operator</th>")
        assertThat(html).contains("<th>Class</th>")
        assertThat(html).contains("<th>Method</th>")
        assertThat(html).contains("<th>Line</th>")
        assertThat(html).contains("<th>Status</th>")
        assertThat(html).contains("com.example.MyClass")
        assertThat(html).contains("doSomething")
    }

    @Test
    fun `generate marks KILLED mutations with killed CSS class`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, MutationStatus.KILLED)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("class=\"killed\"")
    }

    @Test
    fun `generate marks SURVIVED mutations with survived CSS class`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, MutationStatus.SURVIVED)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("class=\"survived\"")
    }

    @Test
    fun `generate marks ERROR mutations with error CSS class`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, MutationStatus.ERROR, errorMessage = "Something broke")
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("class=\"error\"")
    }

    @Test
    fun `generate marks TIMEOUT mutations without special CSS class`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, MutationStatus.TIMEOUT)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        // TIMEOUT status should still appear in the HTML
        assertThat(html).contains("TIMEOUT")
    }

    @Test
    fun `empty report produces valid HTML`() {
        val report = createReport(emptyList())
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).isNotEmpty()
        assertThat(html).startsWith("<!DOCTYPE html>")
        assertThat(html).endsWith("</html>\n")
    }

    @Test
    fun `report with 100 plus mutations produces valid HTML`() {
        val mutations =
            (1..120).map { index ->
                createMutationResult(
                    createMutation(
                        id = "mut-$index",
                        className = "com.example.Class$index",
                        methodName = "method$index",
                        lineNumber = index,
                    ),
                )
            }
        val report = createReport(mutations)
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("<!DOCTYPE html>")
        assertThat(html).contains("</html>")
        // Should contain 120 rows in table body
        val trCount = html.split("<tr>").size - 1
        assertThat(trCount).isGreaterThanOrEqualTo(120)
    }

    @Test
    fun `report file is written to outputDir`() {
        val report = createReport(emptyList())
        val outputDir = tempDir.toFile()
        val file = HtmlReportGenerator.generate(report, outputDir)

        assertThat(file).exists()
        assertThat(file.parentFile).isEqualTo(outputDir)
        assertThat(file.name).isEqualTo("mutation-report.html")
    }

    @Test
    fun `generate includes mutation operator name in table`() {
        val mutation = createMutation(operator = MutationOperator.ARITHMETIC)
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("ARITHMETIC")
    }

    @Test
    fun `generate includes mutation line number in table`() {
        val mutation = createMutation(lineNumber = 73)
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains(">73<")
    }

    @Test
    fun `generate includes NO_COVERAGE stat in summary`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, MutationStatus.NO_COVERAGE)
        val report = createReport(listOf(result))
        val file = HtmlReportGenerator.generate(report, tempDir.toFile())
        val html = file.readText()

        assertThat(html).contains("No Coverage")
    }
}
