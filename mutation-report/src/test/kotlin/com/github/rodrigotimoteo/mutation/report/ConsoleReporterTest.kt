package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConsoleReporterTest {
    private lateinit var reporter: ConsoleReporter

    @BeforeEach
    fun setUp() {
        reporter = ConsoleReporter()
    }

    @Test
    fun `progress returns formatted string`() {
        val result = reporter.progress(50, 100, "TestMutation")
        assertTrue(result.contains("50/100"), "expected 50/100 in progress, got: $result")
        assertTrue(result.contains("50.0%"), "expected 50.0% in progress, got: $result")
        assertTrue(result.contains("TestMutation"), "expected mutation name in progress, got: $result")
    }

    @Test
    fun `progress handles 0 percent`() {
        val result = reporter.progress(0, 100, "TestMutation")
        assertTrue(result.contains("0/100"), "expected 0/100 in progress, got: $result")
        assertTrue(result.contains("0.0%"), "expected 0.0% in progress, got: $result")
    }

    @Test
    fun `progress handles 100 percent`() {
        val result = reporter.progress(100, 100, "TestMutation")
        assertTrue(result.contains("100/100"), "expected 100/100 in progress, got: $result")
        assertTrue(result.contains("100.0%"), "expected 100.0% in progress, got: $result")
    }

    @Test
    fun `generate returns formatted report`() {
        val report = createTestReport()
        val result = reporter.generate(report)
        assertTrue(result.contains("MutKt Mutation Report"), "expected title in report")
        assertTrue(result.contains("Total Mutations:    10"), "expected total=10 line, got:\n$result")
        assertTrue(result.contains("Killed:             7"), "expected killed=7 line, got:\n$result")
        assertTrue(result.contains("Survived:           2"), "expected survived=2 line, got:\n$result")
        assertTrue(result.contains("Kill Rate:"), "expected kill rate line in report")
    }

    @Test
    fun `generate includes survived mutations`() {
        val report = createTestReport()
        val result = reporter.generate(report)
        assertTrue(result.contains("Survived Mutations"))
        assertTrue(result.contains("com.example.Foo"))
    }

    @Test
    fun `generate includes error mutations`() {
        val report = createTestReportWithErrors()
        val result = reporter.generate(report)
        assertTrue(result.contains("Error Mutations"))
    }

    @Test
    fun `update returns formatted string`() {
        val result = reporter.update(5, 2, "TestMutation")
        assertTrue(result.contains("Killed: 5"), "expected 'Killed: 5', got: $result")
        assertTrue(result.contains("Survived: 2"), "expected 'Survived: 2', got: $result")
        assertTrue(result.contains("Kill Rate:"), "expected 'Kill Rate:' in update")
        assertTrue(result.contains("TestMutation"), "expected mutation name in update, got: $result")
    }

    @Test
    fun `update handles zero killed`() {
        val result = reporter.update(0, 5, "TestMutation")
        assertTrue(result.contains("Kill Rate: 0.0%"), "expected 'Kill Rate: 0.0%', got: $result")
    }

    @Test
    fun `clearLine returns escape sequence`() {
        val result = reporter.clearLine()
        assertEquals("\r\u001B[K", result)
    }

    private fun createTestReport(): MutationReport {
        val results = mutableListOf<MutationResult>()

        // 7 killed
        for (i in 1..7) {
            results.add(
                MutationResult(
                    mutation = createMutation("com.example.Foo", "bar$i", i * 10),
                    status = MutationStatus.KILLED,
                    executionTimeMs = 100,
                ),
            )
        }

        // 2 survived
        for (i in 1..2) {
            results.add(
                MutationResult(
                    mutation = createMutation("com.example.Foo", "baz$i", i * 20),
                    status = MutationStatus.SURVIVED,
                    executionTimeMs = 100,
                ),
            )
        }

        // 1 no coverage
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

    private fun createTestReportWithErrors(): MutationReport {
        val results =
            listOf(
                MutationResult(
                    mutation = createMutation("com.example.Foo", "bar", 10),
                    status = MutationStatus.ERROR,
                    errorMessage = "NullPointerException",
                    executionTimeMs = 50,
                ),
            )

        return MutationReport(
            results = results,
            totalMutations = 1,
            killedMutations = 0,
            survivedMutations = 0,
            errorMutations = 1,
            timeoutMutations = 0,
            noCoverageMutations = 0,
            totalExecutionTimeMs = 50,
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
