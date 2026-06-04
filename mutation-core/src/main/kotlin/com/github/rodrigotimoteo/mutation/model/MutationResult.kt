package com.github.rodrigotimoteo.mutation.model

/**
 * Result of testing a single mutation.
 *
 * Contains the mutation, its status after test execution, execution time,
 * and any error message if the mutation caused an error.
 *
 * @property mutation The mutation that was tested
 * @property status Status of the mutation (KILLED, SURVIVED, ERROR, TIMEOUT, NO_COVERAGE)
 * @property executionTimeMs Time taken to execute tests against this mutation
 * @property errorMessage Error message if status is ERROR
 * @see MutationStatus for status details
 */
data class MutationResult(
    val mutation: Mutation,
    val status: MutationStatus,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null,
) {
    val isKilled: Boolean get() = status == MutationStatus.KILLED
    val isSurvived: Boolean get() = status == MutationStatus.SURVIVED
}

/**
 * Mutation score for a specific class.
 */
data class ClassMutationScore(
    val className: String,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val weakMutations: Int = 0,
    val subsumedMutations: Int = 0,
    val score: Int = if (totalMutations > 0) (killedMutations * 100) / totalMutations else 0,
)

/**
 * Complete mutation testing report with results and statistics.
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
) {
    val killedPercentage: Int get() =
        if (totalMutations > 0) (killedMutations * 100) / totalMutations else 0

    val survivedPercentage: Int get() =
        if (totalMutations > 0) (survivedMutations * 100) / totalMutations else 0

    /**
     * Get per-class mutation scores.
     */
    fun getClassScores(): List<ClassMutationScore> {
        return results
            .groupBy { it.mutation.className }
            .map { (className, classResults) ->
                ClassMutationScore(
                    className = className,
                    totalMutations = classResults.size,
                    killedMutations = classResults.count { it.isKilled },
                    survivedMutations = classResults.count { it.isSurvived },
                )
            }
            .sortedByDescending { it.score }
    }
}
