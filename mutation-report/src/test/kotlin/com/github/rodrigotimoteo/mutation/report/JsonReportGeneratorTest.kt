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

class JsonReportGeneratorTest {
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
        errorMessage: String? = null,
    ): MutationResult {
        return MutationResult(
            mutation = createMutation(id),
            status = status,
            executionTimeMs = 100,
            errorMessage = errorMessage,
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
    fun `generate produces valid JSON file`() {
        val result = createResult("m1", MutationStatus.KILLED)
        val report = createReport(result)

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        assertTrue(file.exists())
        assertEquals("mutations.json", file.name)
    }

    @Test
    fun `generate includes statistics section`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
                createResult("m3", MutationStatus.KILLED),
            )
        val report = createReport(*results.toTypedArray())

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "\"statistics\"")
        assertContains(content, "\"mutations\": 3")
        assertContains(content, "\"killed\": 2")
        assertContains(content, "\"survived\": 1")
    }

    @Test
    fun `generate includes all mutation results`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
            )
        val report = createReport(*results.toTypedArray())

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "\"id\": \"m1\"")
        assertContains(content, "\"id\": \"m2\"")
        assertContains(content, "\"status\": \"KILLED\"")
        assertContains(content, "\"status\": \"SURVIVED\"")
    }

    @Test
    fun `generate includes error message for ERROR status`() {
        val result = createResult("m1", MutationStatus.ERROR, errorMessage = "Something went wrong")
        val report = createReport(result)

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "\"errorMessage\"")
        assertContains(content, "Something went wrong")
    }

    @Test
    fun `empty report generates valid JSON`() {
        val report = createReport()

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "{")
        assertContains(content, "}")
        assertContains(content, "\"mutations\": 0")
    }

    @Test
    fun `special characters in class names are properly escaped`() {
        val mutation =
            Mutation(
                id = "m1",
                className = "com.\"Foo\"\\Bar",
                methodName = "testMethod",
                methodDescriptor = "()V",
                operator = MutationOperator.ARITHMETIC,
                lineNumber = 10,
                originalBytecode = ByteArray(0),
                mutatedBytecode = ByteArray(0),
                description = "Line1\nLine2\rTab\there",
            )
        val result = MutationResult(mutation = mutation, status = MutationStatus.KILLED)
        val report = createReport(result)

        val file = JsonReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        // Backslash, quote, and control chars should be escaped
        assertContains(content, "\\\"")
        assertContains(content, "\\\\")
    }

    @Test
    fun `generate returns correct file path`() {
        val report = createReport()
        val file = JsonReportGenerator.generate(report, tempDir.toFile())

        assertEquals(
            File(tempDir.toFile(), "mutations.json").absolutePath,
            file.absolutePath,
        )
    }
}
