package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus

/**
 * Orders tests to run most effective tests first.
 *
 * Tests that killed the most mutants in previous runs are run first.
 * This provides faster feedback on mutation score.
 */
class TestOrderingStrategy {
    /**
     * Test kill statistics.
     */
    data class TestKillCount(
        val testId: String,
        val killCount: Int,
        val lastRun: Long = System.currentTimeMillis(),
    )

    /**
     * Order tests by kill count (descending).
     *
     * @param testIds List of test class names
     * @param history Previous test kill counts
     * @return Ordered list of test IDs
     */
    fun orderTests(
        testIds: List<String>,
        history: Map<String, TestKillCount> = emptyMap(),
    ): List<String> {
        return testIds.sortedByDescending { testId ->
            history[testId]?.killCount ?: 0
        }
    }

    /**
     * Get top-k tests that are most likely to kill mutants.
     *
     * @param testIds List of test class names
     * @param history Previous test kill counts
     * @param k Number of top tests to return
     * @return Top-k test IDs
     */
    fun getTopKTests(
        testIds: List<String>,
        history: Map<String, TestKillCount>,
        k: Int = 5,
    ): List<String> {
        return orderTests(testIds, history).take(k)
    }

    /**
     * Update history with new kill counts.
     *
     * @param history Current history
     * @param results New mutation test results
     * @return Updated history
     */
    fun updateHistory(
        history: Map<String, TestKillCount>,
        results: List<MutationResult>,
    ): Map<String, TestKillCount> {
        val updatedHistory = history.toMutableMap()

        for (result in results) {
            if (result.status == MutationStatus.KILLED) {
                val testId = extractTestId(result)
                val current = updatedHistory[testId]
                val newKillCount = (current?.killCount ?: 0) + 1

                updatedHistory[testId] =
                    TestKillCount(
                        testId = testId,
                        killCount = newKillCount,
                        lastRun = System.currentTimeMillis(),
                    )
            }
        }

        return updatedHistory
    }

    /**
     * Extract test ID from mutation result.
     */
    private fun extractTestId(result: MutationResult): String {
        // Use className as test identifier
        return result.mutation.className
    }

    /**
     * Calculate kill percentage for each test.
     *
     * @param history Test kill counts
     * @return Map of test ID to kill percentage
     */
    fun getKillPercentages(history: Map<String, TestKillCount>): Map<String, Double> {
        val totalKills = history.values.sumOf { it.killCount }
        if (totalKills == 0) return emptyMap()

        return history.mapValues { (_, count) ->
            (count.killCount.toDouble() / totalKills) * 100.0
        }
    }
}
