package com.github.rodrigotimoteo.mutation.model

import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import kotlin.collections.ArrayList

/**
 * Represents a single mutation that can be applied to bytecode.
 */
data class Mutation(
    val id: String,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val operator: MutationOperator,
    val lineNumber: Int,
    val originalBytecode: ByteArray,
    val mutatedBytecode: ByteArray,
    val description: String
) {
    override fun toString(): String {
        return "Mutation($id: $className.$methodName ${operator.name} at line $lineNumber)"
    }
}

/**
 * Result of running tests against a mutant.
 */
enum class MutationStatus {
    KILLED,       // Tests failed - mutation detected
    SURVIVED,     // Tests passed - mutation not detected
    ERROR,        // Test execution error
    TIMEOUT,      // Test timed out
    NO_COVERAGE   // No tests cover this mutation
}

data class MutationResult(
    val mutation: Mutation,
    val status: MutationStatus,
    val killingTest: String? = null,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null
) {
    val isKilled: Boolean get() = status == MutationStatus.KILLED
    val isSurvived: Boolean get() = status == MutationStatus.SURVIVED
}

/**
 * Aggregated mutation testing report.
 */
data class MutationReport(
    val results: List<MutationResult>,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val errorMutations: Int,
    val timeoutMutations: Int,
    val noCoverageMutations: Int,
    val totalExecutionTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val mutationScore: Double
        get() = if (totalMutations > 0) (killedMutations.toDouble() / totalMutations * 100) else 0.0

    val testStrength: Double
        get() = if (killedMutations + survivedMutations > 0)
            killedMutations.toDouble() / (killedMutations + survivedMutations) * 100
        else 0.0

    fun printSummary() {
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║              MUTATION TESTING REPORT                         ║")
        println("╠══════════════════════════════════════════════════════════════╣")
        println("║ Total Mutations:      ${"%5d".format(totalMutations)}                                    ║")
        println("║ Killed:               ${"%5d".format(killedMutations)}      (${"%5.1f".format(mutationScore)}%)              ║")
        println("║ Survived:             ${"%5d".format(survivedMutations)}      (${"%5.1f".format(100 - mutationScore)}%)              ║")
        println("║ Errors:               ${"%5d".format(errorMutations)}                                    ║")
        println("║ Timeouts:             ${"%5d".format(timeoutMutations)}                                    ║")
        println("║ No Coverage:          ${"%5d".format(noCoverageMutations)}                                    ║")
        println("║ Test Strength:        ${"%5.1f".format(testStrength)}%                                    ║")
        println("║ Total Time:           ${"%5d".format(totalExecutionTimeMs)} ms                                    ║")
        println("╚══════════════════════════════════════════════════════════════╝")
    }
}