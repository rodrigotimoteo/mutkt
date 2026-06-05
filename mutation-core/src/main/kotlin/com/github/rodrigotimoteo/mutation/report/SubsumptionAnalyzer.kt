package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus

/**
 * Analyzes mutation subsumption to skip redundant mutants.
 *
 * Subsumption: If all tests that kill M2 also kill M1, then M1 subsumes M2.
 * M2 can be skipped because it's guaranteed to be killed if M1 is killed.
 */
class SubsumptionAnalyzer {
    /**
     * Result of subsumption analysis.
     */
    data class SubsumptionResult(
        val subsumedMutations: List<Mutation>,
        // M2 -> M1 (M1 subsumes M2)
        val subsumedBy: Map<String, String>,
        val skippedCount: Int,
    )

    /**
     * Analyze subsumption among mutations.
     *
     * @param results List of mutation test results
     * @return SubsumptionResult with subsumed mutations
     */
    fun analyze(results: List<MutationResult>): SubsumptionResult {
        if (results.size < 2) {
            return SubsumptionResult(emptyList(), emptyMap(), 0)
        }

        // Build kill matrix: mutationId -> set of test names that killed it
        val killMatrix = buildKillMatrix(results)

        // Find subsumption relationships
        val subsumedBy = mutableMapOf<String, String>()
        val mutationIds = results.map { it.mutation.id }

        for (i in mutationIds.indices) {
            val m1 = mutationIds[i]
            val killM1 = killMatrix[m1] ?: continue

            for (j in i + 1 until mutationIds.size) {
                val m2 = mutationIds[j]
                val killM2 = killMatrix[m2] ?: continue

                // M1 subsumes M2 if kill(M1) is subset of kill(M2)
                if (killM1.isNotEmpty() && killM2.containsAll(killM1)) {
                    // M1 subsumes M2 (M2 is redundant)
                    if (m2 !in subsumedBy) {
                        subsumedBy[m2] = m1
                    }
                }

                // M2 subsumes M1 if kill(M2) is subset of kill(M1)
                if (killM2.isNotEmpty() && killM1.containsAll(killM2)) {
                    // M2 subsumes M1 (M1 is redundant)
                    if (m1 !in subsumedBy) {
                        subsumedBy[m1] = m2
                    }
                }
            }
        }

        // Collect subsumed mutations
        val subsumedMutations =
            results
                .filter { it.mutation.id in subsumedBy }
                .map { it.mutation }

        return SubsumptionResult(
            subsumedMutations = subsumedMutations,
            subsumedBy = subsumedBy,
            skippedCount = subsumedMutations.size,
        )
    }

    /**
     * Build kill matrix from results.
     */
    private fun buildKillMatrix(results: List<MutationResult>): Map<String, Set<String>> {
        val killMatrix = mutableMapOf<String, MutableSet<String>>()

        for (result in results) {
            if (result.status == MutationStatus.KILLED) {
                val mutationId = result.mutation.id
                // Use the mutated class name as the test name proxy.
                // In a complete implementation, this would track which specific
                // test class killed each mutation.
                val testName = result.mutation.className
                killMatrix.getOrPut(mutationId) { mutableSetOf() }.add(testName)
            }
        }

        return killMatrix
    }
}
