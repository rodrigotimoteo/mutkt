package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Test strength ordering based on historical kill data.
 *
 * Tracks which tests kill the most mutations and runs them first.
 * This enables faster kill detection by exercising strongest tests early.
 *
 * History stored in `.mutkt/test-strength.json`
 *
 * Thread safety: [inMemoryCache] is a [ConcurrentHashMap] so concurrent
 * Gradle workers can call [recordResults] without corrupting the cache.
 * [flushHistory] is wrapped in [withFileLock] (OS file lock on the
 * `test-strength.json.lock` sidecar) so concurrent workers cannot
 * write a torn JSON file.
 *
 * Usage:
 * ```kotlin
 * val ordering = TestStrengthOrdering(projectDir)
 * val ordered = ordering.orderTests(testClassNames)
 * // Tests that historically kill more mutations come first
 * ```
 */
class TestStrengthOrdering(private val projectDir: File) {
    private val historyFile = File(projectDir, "$MUTKT_DIR/test-strength.json")
    private val inMemoryCache: ConcurrentHashMap<String, TestStrengthEntry> = ConcurrentHashMap()
    private var cacheLoaded = false

    /**
     * Order tests by historical kill strength.
     *
     * @param testClassNames List of test class names
     * @return Ordered list with strongest tests first
     */
    fun orderTests(testClassNames: List<String>): List<String> {
        ensureCacheLoaded()
        if (inMemoryCache.isEmpty()) return testClassNames

        return testClassNames.sortedByDescending { testClass ->
            inMemoryCache[testClass]?.totalKills ?: 0
        }
    }

    /**
     * Record test results for strength tracking.
     * Accumulates in memory — call flushHistory() to persist.
     */
    fun recordResults(
        testClassName: String,
        killedMutations: Int,
        totalMutations: Int,
    ) {
        ensureCacheLoaded()
        // Atomic merge: read existing entry, apply delta, and write back via
        // ConcurrentHashMap.compute so concurrent Gradle workers cannot lose
        // increments. Without compute, two parallel recordResults calls for
        // the same test class would each read the old entry, then both
        // overwrite it with their copy — losing the first call's increment.
        inMemoryCache.compute(testClassName) { _, existing ->
            val current = existing ?: TestStrengthEntry()
            current.copy(
                totalKills = current.totalKills + killedMutations,
                totalRuns = current.totalRuns + 1,
                totalMutations = current.totalMutations + totalMutations,
                lastRun = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Flush in-memory cache to disk. Call once after all recordResults calls.
     *
     * Wrapped in [withFileLock] so concurrent Gradle workers cannot
     * interleave their `writeText` calls and produce a torn JSON file.
     */
    fun flushHistory() {
        ensureCacheLoaded()
        withFileLock {
            saveHistory(inMemoryCache)
        }
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
        ensureCacheLoaded()
        val entry = inMemoryCache[testClassName] ?: return 0.0
        if (entry.totalMutations == 0) return 0.0
        return entry.totalKills.toDouble() / entry.totalMutations
    }

    /**
     * Get statistics for all tracked tests.
     */
    fun getStats(): Map<String, TestStrengthEntry> {
        ensureCacheLoaded()
        return inMemoryCache.toMap()
    }

    /**
     * Clear all history data.
     */
    fun clear() {
        inMemoryCache.clear()
        cacheLoaded = false
        historyFile.delete()
    }

    private fun ensureCacheLoaded() {
        if (!cacheLoaded) {
            inMemoryCache.putAll(loadHistory())
            cacheLoaded = true
        }
    }

    private fun loadHistory(): Map<String, TestStrengthEntry> {
        if (!historyFile.exists()) return emptyMap()

        val content = historyFile.readText()
        val entries = mutableMapOf<String, TestStrengthEntry>()

        // Field detection uses regex anchored at line start so partial
        // matches in string values (e.g. a test class name containing
        // "totalKills") cannot corrupt a numeric field. Field order is
        // arbitrary: each line is classified independently and the
        // entry is committed when the closing `}` is seen.
        val fieldRegex = Regex("\"(totalKills|totalRuns|totalMutations|lastRun)\"\\s*:\\s*(-?\\d+)")

        var currentTest: String? = null
        var totalKills = 0
        var totalRuns = 0
        var totalMutations = 0
        var lastRun = 0L

        fun commit() {
            val test = currentTest ?: return
            entries[test] =
                TestStrengthEntry(
                    totalKills = totalKills,
                    totalRuns = totalRuns,
                    totalMutations = totalMutations,
                    lastRun = lastRun,
                )
        }

        for (line in content.lines()) {
            val trimmed = line.trim()
            when {
                // Opening of an entry: "test class name": {
                trimmed.startsWith("\"") && trimmed.endsWith("{") -> {
                    val testName = trimmed.substringAfter("\"").substringBeforeLast("\"")
                    if (testName.isNotEmpty()) {
                        currentTest = testName
                        totalKills = 0
                        totalRuns = 0
                        totalMutations = 0
                        lastRun = 0L
                    }
                }
                trimmed == "}," || trimmed == "}" -> commit().also { currentTest = null }
                else -> {
                    val match = fieldRegex.find(trimmed) ?: continue
                    val (name, raw) = match.destructured
                    when (name) {
                        "totalKills" -> totalKills = raw.toIntOrNull() ?: 0
                        "totalRuns" -> totalRuns = raw.toIntOrNull() ?: 0
                        "totalMutations" -> totalMutations = raw.toIntOrNull() ?: 0
                        "lastRun" -> lastRun = raw.toLongOrNull() ?: 0L
                    }
                }
            }
        }
        // File may end without a trailing newline + `}`; commit any open entry.
        commit()

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

    /**
     * Run [block] while holding an exclusive OS file lock on the test
     * strength history file. Prevents concurrent Gradle workers from
     * interleaving their `writeText` calls and corrupting the JSON.
     *
     * The `.lock` sidecar is removed in `finally` so it does not
     * accumulate in `.mutkt/`.
     */
    private inline fun <T> withFileLock(block: () -> T): T {
        val parent = historyFile.parentFile
        if (parent != null) parent.mkdirs()
        val lockFile = File(parent, "${historyFile.name}.lock")
        try {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                channel.lock().use {
                    return block()
                }
            }
        } finally {
            lockFile.delete()
        }
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
