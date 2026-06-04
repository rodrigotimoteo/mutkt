package com.github.rodrigotimoteo.mutation.model

/**
 * Result of testing a single mutation.
 */
data class MutationResult(
    val mutation: Mutation,
    val status: MutationStatus,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null
) {
    val isKilled: Boolean get() = status == MutationStatus.KILLED
    val isSurvived: Boolean get() = status == MutationStatus.SURVIVED
}

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
    val totalExecutionTimeMs: Long
) {
    val killedPercentage: Int get() =
        if (totalMutations > 0) (killedMutations * 100) / totalMutations else 0

    val survivedPercentage: Int get() =
        if (totalMutations > 0) (survivedMutations * 100) / totalMutations else 0
}