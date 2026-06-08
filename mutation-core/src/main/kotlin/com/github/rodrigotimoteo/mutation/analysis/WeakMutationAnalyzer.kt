package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo

/**
 * Weak mutation analyzer using coverage data.
 *
 * Detects mutations that are never reached by any test (NO_COVERAGE).
 * Uses JaCoCo execution data to determine if mutation points were executed.
 *
 * Weak mutation: If a mutation is never executed, it cannot be killed or survived.
 * These mutations are marked as NO_COVERAGE and skipped during testing.
 *
 * Usage:
 * ```kotlin
 * val analyzer = WeakMutationAnalyzer()
 * val reachable = analyzer.filterReachable(mutations, coverageData)
 * // reachable: mutations that were actually executed
 * ```
 */
class WeakMutationAnalyzer {
    /**
     * Filter mutations to only those reached by tests.
     *
     * @param mutations All mutations found
     * @param coveredLines Set of line numbers covered by tests (from JaCoCo)
     * @return Mutations that were actually executed
     */
    fun filterReachable(
        mutations: List<MutationInfo>,
        coveredLines: Set<Int>,
    ): List<MutationInfo> {
        if (coveredLines.isEmpty()) return mutations // No coverage data = treat all as reachable

        return mutations.filter { mutation ->
            mutation.lineNumber in coveredLines
        }
    }

    /**
     * Classify mutations by reachability.
     *
     * @param mutations All mutations found
     * @param coveredLines Set of line numbers covered by tests
     * @return Pair of (reachable mutations, unreachable mutations)
     */
    fun classifyByReachability(
        mutations: List<MutationInfo>,
        coveredLines: Set<Int>,
    ): Pair<List<MutationInfo>, List<MutationInfo>> {
        if (coveredLines.isEmpty()) return mutations to emptyList()

        val reachable = mutableListOf<MutationInfo>()
        val unreachable = mutableListOf<MutationInfo>()

        for (mutation in mutations) {
            if (mutation.lineNumber in coveredLines) {
                reachable.add(mutation)
            } else {
                unreachable.add(mutation)
            }
        }

        return reachable to unreachable
    }

    /**
     * Get weak mutation statistics.
     *
     * @param mutations All mutations
     * @param coveredLines Covered lines from JaCoCo
     * @return WeakMutationStats with counts and percentages
     */
    fun getStats(
        mutations: List<MutationInfo>,
        coveredLines: Set<Int>,
    ): WeakMutationStats {
        val (reachable, unreachable) = classifyByReachability(mutations, coveredLines)
        val total = mutations.size
        val reachableCount = reachable.size
        val unreachableCount = unreachable.size

        return WeakMutationStats(
            totalMutations = total,
            reachableMutations = reachableCount,
            unreachableMutations = unreachableCount,
            reachablePercentage = if (total > 0) reachableCount.toDouble() / total * 100 else 0.0,
        )
    }
}

/**
 * Statistics for weak mutation analysis.
 *
 * @property totalMutations Total mutations found
 * @property reachableMutations Mutations that were executed
 * @property unreachableMutations Mutations never executed
 * @property reachablePercentage Percentage of reachable mutations
 */
data class WeakMutationStats(
    val totalMutations: Int,
    val reachableMutations: Int,
    val unreachableMutations: Int,
    val reachablePercentage: Double,
)
