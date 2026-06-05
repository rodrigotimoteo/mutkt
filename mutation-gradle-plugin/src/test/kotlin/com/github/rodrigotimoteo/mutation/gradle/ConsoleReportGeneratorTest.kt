package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsoleReportGeneratorTest {
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
    fun `generate produces output with summary header`() {
        val report = createReport(emptyList())
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Mutation Test Report")
        assertThat(output).contains("====")
    }

    @Test
    fun `generate includes total mutations count`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation)
        val report = createReport(listOf(result, result.copy(mutation = createMutation(id = "m2"))))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Total mutations:  2")
    }

    @Test
    fun `generate includes killed and survived counts`() {
        val killed = createMutationResult(createMutation(id = "m1"), MutationStatus.KILLED)
        val survived = createMutationResult(createMutation(id = "m2"), MutationStatus.SURVIVED)
        val report = createReport(listOf(killed, survived))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Killed:           1 (50%)")
        assertThat(output).contains("Survived:         1 (50%)")
    }

    @Test
    fun `generate includes error and timeout counts`() {
        val error = createMutationResult(createMutation(id = "m1"), MutationStatus.ERROR)
        val timeout = createMutationResult(createMutation(id = "m2"), MutationStatus.TIMEOUT)
        val report = createReport(listOf(error, timeout))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Errors:           1")
        assertThat(output).contains("Timeouts:         1")
    }

    @Test
    fun `generate includes no coverage count`() {
        val noCov = createMutationResult(createMutation(id = "m1"), MutationStatus.NO_COVERAGE)
        val report = createReport(listOf(noCov))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("No coverage:      1")
    }

    @Test
    fun `generate includes total execution time in seconds`() {
        val mutation = createMutation()
        val result = createMutationResult(mutation, executionTimeMs = 5000)
        val report = createReport(listOf(result))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Total time:       5.0s")
    }

    @Test
    fun `generate lists surviving mutants with operator class method and line`() {
        val mutation =
            createMutation(
                id = "mut-1",
                className = "com.example.Foo",
                methodName = "bar",
                operator = MutationOperator.ARITHMETIC,
                lineNumber = 99,
                description = "Replaced + with -",
            )
        val result = createMutationResult(mutation, MutationStatus.SURVIVED)
        val report = createReport(listOf(result))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Surviving mutants:")
        assertThat(output).contains("[ARITHMETIC]")
        assertThat(output).contains("com.example.Foo.bar:99")
        assertThat(output).contains("Replaced + with -")
    }

    @Test
    fun `generate skips surviving list when no survivors`() {
        val killed = createMutationResult(createMutation(), MutationStatus.KILLED)
        val report = createReport(listOf(killed))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).doesNotContain("Surviving mutants:")
    }

    @Test
    fun `empty report still produces header`() {
        val report = createReport(emptyList())
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Mutation Test Report")
        assertThat(output).contains("Total mutations:  0")
        assertThat(output).contains("Killed:           0 (0%)")
    }

    @Test
    fun `generate includes killed percentage`() {
        val killed1 = createMutationResult(createMutation(id = "m1"), MutationStatus.KILLED)
        val killed2 = createMutationResult(createMutation(id = "m2"), MutationStatus.KILLED)
        val killed3 = createMutationResult(createMutation(id = "m3"), MutationStatus.KILLED)
        val survived = createMutationResult(createMutation(id = "m4"), MutationStatus.SURVIVED)
        val report = createReport(listOf(killed1, killed2, killed3, survived))
        val output = ConsoleReportGenerator.generate(report)

        assertThat(output).contains("Killed:           3 (75%)")
        assertThat(output).contains("Survived:         1 (25%)")
    }
}
