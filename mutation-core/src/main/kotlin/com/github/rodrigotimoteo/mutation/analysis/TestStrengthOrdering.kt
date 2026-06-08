package com.github.rodrigotimoteo.mutation.analysis

import java.io.File

/**
 * Test strength ordering based on historical kill data.
 *
 * Tracks which tests kill the most mutations and runs them first.
 * This enables faster kill detection by exercising strongest tests early.
 *
 * History stored in `.mutkt/test-strength.json`
 *
 * Usage:
 * ```kotlin
 * val ordering = TestStrengthOrdering(projectDir)
 * val ordered = ordering.orderTests(testClassNames)
 * // Tests that historically kill more mutations come first
 * ```
 */
class TestStrengthOrdering(private val projectDir: File) {
    private val historyFile = File(projectDir, ".mutkt/test-strength.json")

    /**
     * Order tests by historical kill strength.
     *
     * @param testClassNames List of test class names
     * @return Ordered list with strongest tests first
     */
    fun orderTests(testClassNames: List<String>): List<String> {
        val history = loadHistory()
        if (history.isEmpty()) return testClassNames

        return testClassNames.sortedByDescending { testClass ->
            history[testClass]?.totalKills ?: 0
        }
    }

    /**
     * Record test results for strength tracking.
     *
     * @param testClassName Test class that was executed
     * @param killedMutations Number of mutations killed by this test
     * @param totalMutations Total mutations executed
     */
    fun recordResults(
        testClassName: String,
        killedMutations: Int,
        totalMutations: Int,
    ) {
        val history = loadHistory().toMutableMap()
        val existing = history[testClassName] ?: TestStrengthEntry()

        history[testClassName] =
            existing.copy(
                totalKills = existing.totalKills + killedMutations,
                totalRuns = existing.totalRuns + 1,
                totalMutations = existing.totalMutations + totalMutations,
                lastRun = System.currentTimeMillis(),
            )

        saveHistory(history)
    }

    /**
     * Get strength score for a test class.
     *
     * Score = kills / total mutations tested (higher = stronger)
     *
     * @param testClassName Test class name
     * @return Strength score (0.0 to 1.0)
     */
    fun getStrengthScore(testClassName: String): Double {
        val history = loadHistory()
        val entry = history[testClassName] ?: return 0.0
        if (entry.totalMutations == 0) return 0.0
        return entry.totalKills.toDouble() / entry.totalMutations
    }

    /**
     * Get statistics for all tracked tests.
     *
     * @return Map of test class name to strength entry
     */
    fun getStats(): Map<String, TestStrengthEntry> = loadHistory()

    /**
     * Clear all history data.
     */
    fun clear() {
        historyFile.delete()
    }

    private fun loadHistory(): Map<String, TestStrengthEntry> {
        if (!historyFile.exists()) return emptyMap()

        val content = historyFile.readText()
        val entries = mutableMapOf<String, TestStrengthEntry>()

        // Simple JSON parsing
        var currentTest: String? = null
        var totalKills = 0
        var totalRuns = 0
        var totalMutations = 0
        var lastRun = 0L

        for (line in content.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.matches(Regex("^\"[^\"]+\":\\s*\\{$")) -> {
                    currentTest = trimmed.substringAfter("\"").substringBefore("\"")
                }
                trimmed.contains("\"totalKills\":") -> {
                    totalKills = trimmed.substringAfter(":").trim().removeSuffix(",").toIntOrNull() ?: 0
                }
                trimmed.contains("\"totalRuns\":") -> {
                    totalRuns = trimmed.substringAfter(":").trim().removeSuffix(",").toIntOrNull() ?: 0
                }
                trimmed.contains("\"totalMutations\":") -> {
                    totalMutations = trimmed.substringAfter(":").trim().removeSuffix(",").toIntOrNull() ?: 0
                }
                trimmed.contains("\"lastRun\":") -> {
                    lastRun = trimmed.substringAfter(":").trim().removeSuffix(",").toLongOrNull() ?: 0
                }
                trimmed == "}," || trimmed == "}" -> {
                    if (currentTest != null) {
                        entries[currentTest!!] =
                            TestStrengthEntry(
                                totalKills = totalKills,
                                totalRuns = totalRuns,
                                totalMutations = totalMutations,
                                lastRun = lastRun,
                            )
                        currentTest = null
                    }
                }
            }
        }

        return entries
    }

    private fun saveHistory(history: Map<String, TestStrengthEntry>) {
        val json =
            buildString {
                appendLine("{")
                history.entries.forEachIndexed { index, (testClass, entry) ->
                    val comma = if (index < history.size - 1) "," else ""
                    appendLine("  \"$testClass\": {")
                    appendLine("    \"totalKills\": ${entry.totalKills},")
                    appendLine("    \"totalRuns\": ${entry.totalRuns},")
                    appendLine("    \"totalMutations\": ${entry.totalMutations},")
                    appendLine("    \"lastRun\": ${entry.lastRun}")
                    appendLine("  }$comma")
                }
                appendLine("}")
            }
        historyFile.parentFile.mkdirs()
        historyFile.writeText(json)
    }
}

/**
 * Entry tracking test strength over time.
 *
 * @property totalKills Total mutations killed by this test
 * @property totalRuns Total times this test was executed
 * @property totalMutations Total mutations tested against
 * @property lastRun Timestamp of last execution
 */
data class TestStrengthEntry(
    val totalKills: Int = 0,
    val totalRuns: Int = 0,
    val totalMutations: Int = 0,
    val lastRun: Long = 0,
)
