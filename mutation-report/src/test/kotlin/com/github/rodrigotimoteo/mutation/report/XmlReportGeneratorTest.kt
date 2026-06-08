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
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlReportGeneratorTest {
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
    fun `generate produces valid XML that parses without error`() {
        val report = createReport(createResult("m1", MutationStatus.KILLED))
        val file = XmlReportGenerator.generate(report, tempDir.toFile())

        assertTrue(file.exists())
        assertEquals("mutations.xml", file.name)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        assertEquals("mutationTestReport", doc.documentElement.tagName)
    }

    @Test
    fun `generate includes mutations element with children`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
            )
        val report = createReport(*results.toTypedArray())

        val file = XmlReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "<mutations>")
        assertContains(content, "<mutation>")
        assertContains(content, "<id>m1</id>")
        assertContains(content, "<id>m2</id>")
        assertContains(content, "<status>KILLED</status>")
        assertContains(content, "<status>SURVIVED</status>")
    }

    @Test
    fun `generate includes statistics section`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.KILLED),
                createResult("m3", MutationStatus.SURVIVED),
            )
        val report = createReport(*results.toTypedArray())

        val file = XmlReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "<statistics>")
        assertContains(content, "<mutations>3</mutations>")
        assertContains(content, "<killed>2</killed>")
        assertContains(content, "<survived>1</survived>")
    }

    @Test
    fun `empty report produces valid XML`() {
        val report = createReport()
        val file = XmlReportGenerator.generate(report, tempDir.toFile())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        assertEquals("mutationTestReport", doc.documentElement.tagName)
    }

    @Test
    fun `generate handles all status types`() {
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
                createResult("m3", MutationStatus.ERROR),
                createResult("m4", MutationStatus.TIMEOUT),
                createResult("m5", MutationStatus.NO_COVERAGE),
            )
        val report = createReport(*results.toTypedArray())

        val file = XmlReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        assertContains(content, "KILLED")
        assertContains(content, "SURVIVED")
        assertContains(content, "ERROR")
        assertContains(content, "TIMEOUT")
        assertContains(content, "NO_COVERAGE")
    }

    @Test
    fun `special characters are XML-escaped`() {
        val mutation =
            Mutation(
                id = "id<>&\"'",
                className = "com.<Foo>&Bar",
                methodName = "test \"quoted\" method",
                methodDescriptor = "()V",
                operator = MutationOperator.ARITHMETIC,
                lineNumber = 10,
                originalBytecode = ByteArray(0),
                mutatedBytecode = ByteArray(0),
                description = "desc with <tags> & \"quotes\" & 'single'",
            )
        val result = MutationResult(mutation = mutation, status = MutationStatus.KILLED)
        val report = createReport(result)

        val file = XmlReportGenerator.generate(report, tempDir.toFile())
        val content = file.readText()

        // XML-escaped forms should appear
        assertContains(content, "&lt;")
        assertContains(content, "&gt;")
        assertContains(content, "&amp;")
        assertContains(content, "&quot;")
        assertContains(content, "&apos;")
    }

    @Test
    fun `generate returns correct file path`() {
        val report = createReport()
        val file = XmlReportGenerator.generate(report, tempDir.toFile())

        assertEquals(
            File(tempDir.toFile(), "mutations.xml").absolutePath,
            file.absolutePath,
        )
    }
}
