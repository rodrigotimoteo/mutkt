package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import org.slf4j.LoggerFactory
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
class TestStrengthOrdering(
    private val projectDir: File,
    // Injectable clock for deterministic tests. Defaults to wall clock;
    // pass a fixed supplier in unit tests to assert ordering by `lastRun`.
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger(TestStrengthOrdering::class.java)
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
                lastRun = clock(),
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

        val content =
            runCatching { historyFile.readText() }
                .getOrElse { e ->
                    // I/O error reading history should not poison the run —
                    // log and start from an empty history.
                    logger.warn("Failed to read test strength history", e)
                    return emptyMap()
                }
        return parseHistoryJson(content)
    }

    /**
     * Parse the JSON history written by [saveHistory]. Uses a small
     * tokenizer + recursive-descent parser rather than regex-on-raw-text:
     * the previous regex parser broke on test class names that contained
     * `}` or `"` characters, since it scanned line-by-line and could
     * not distinguish a closing brace inside a string from one that
     * terminated the object. The new parser tokenizes the full input
     * with proper escape handling, so embedded braces, quotes, and
     * commas in test class names no longer corrupt the read.
     *
     * Falls back to an empty map on malformed input — a torn history
     * file should not kill the run, the next `flushHistory` will rewrite
     * it from the in-memory cache.
     */
    private fun parseHistoryJson(content: String): Map<String, TestStrengthEntry> {
        val tokens = tokenizeJson(content)
        if (tokens.isEmpty()) return emptyMap()
        var pos = 0
        // Expect top-level `{`
        if (tokens[pos].type != JsonTokenType.LBRACE) return emptyMap()
        pos++

        val entries = mutableMapOf<String, TestStrengthEntry>()

        while (pos < tokens.size && tokens[pos].type != JsonTokenType.RBRACE) {
            // Test class name (string key)
            if (tokens[pos].type != JsonTokenType.STRING) return entries
            val testName = tokens[pos].value
            pos++
            if (pos >= tokens.size || tokens[pos].type != JsonTokenType.COLON) return entries
            pos++
            if (pos >= tokens.size || tokens[pos].type != JsonTokenType.LBRACE) return entries
            pos++

            var totalKills = 0
            var totalRuns = 0
            var totalMutations = 0
            var lastRun = 0L

            while (pos < tokens.size && tokens[pos].type != JsonTokenType.RBRACE) {
                if (tokens[pos].type != JsonTokenType.STRING) return entries
                val fieldName = tokens[pos].value
                pos++
                if (pos >= tokens.size || tokens[pos].type != JsonTokenType.COLON) return entries
                pos++
                // Tolerant value parsing: a missing or non-numeric value
                // (e.g. `"totalKills": notanumber`) defaults to 0 instead
                // of aborting the whole load. The previous regex parser
                // had the same fall-back via `toIntOrNull() ?: 0`; the
                // new tokenizer-based parser must reproduce it so a single
                // corrupted field does not lose the rest of the entry.
                // We still advance `pos` by one so a stray STRING value
                // token does not get misread as the next field name.
                if (pos >= tokens.size || tokens[pos].type != JsonTokenType.NUMBER) {
                    if (pos < tokens.size) pos++
                } else {
                    val raw = tokens[pos].value
                    pos++
                    when (fieldName) {
                        "totalKills" -> totalKills = raw.toIntOrNull() ?: 0
                        "totalRuns" -> totalRuns = raw.toIntOrNull() ?: 0
                        "totalMutations" -> totalMutations = raw.toIntOrNull() ?: 0
                        "lastRun" -> lastRun = raw.toLongOrNull() ?: 0L
                    }
                }
                // Optional comma between fields
                if (pos < tokens.size && tokens[pos].type == JsonTokenType.COMMA) pos++
            }
            // Consume the closing `}` of the entry
            if (pos < tokens.size && tokens[pos].type == JsonTokenType.RBRACE) pos++

            entries[testName] =
                TestStrengthEntry(
                    totalKills = totalKills,
                    totalRuns = totalRuns,
                    totalMutations = totalMutations,
                    lastRun = lastRun,
                )

            // Optional comma between top-level entries
            if (pos < tokens.size && tokens[pos].type == JsonTokenType.COMMA) pos++
        }

        return entries
    }

    private enum class JsonTokenType { LBRACE, RBRACE, COLON, COMMA, STRING, NUMBER }

    private data class JsonToken(
        val type: JsonTokenType,
        val value: String,
    )

    /**
     * Tokenize a JSON document. Handles `{}`, `:`, `,`, string literals
     * (with `\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, and `\uXXXX`
     * escape sequences), and integer literals. Unknown characters are
     * skipped defensively so a partial / corrupted file does not throw
     * during tokenization — the parser will then return early on a
     * structural mismatch.
     */
    private fun tokenizeJson(content: String): List<JsonToken> {
        val tokens = mutableListOf<JsonToken>()
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c.isWhitespace() -> i++
                c == '{' -> {
                    tokens.add(JsonToken(JsonTokenType.LBRACE, "{"))
                    i++
                }
                c == '}' -> {
                    tokens.add(JsonToken(JsonTokenType.RBRACE, "}"))
                    i++
                }
                c == ':' -> {
                    tokens.add(JsonToken(JsonTokenType.COLON, ":"))
                    i++
                }
                c == ',' -> {
                    tokens.add(JsonToken(JsonTokenType.COMMA, ","))
                    i++
                }
                c == '"' -> {
                    val sb = StringBuilder()
                    i++ // skip opening quote
                    while (i < content.length && content[i] != '"') {
                        if (content[i] == '\\' && i + 1 < content.length) {
                            when (val esc = content[i + 1]) {
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                '/' -> sb.append('/')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (i + 5 < content.length) {
                                        val hex = content.substring(i + 2, i + 6)
                                        sb.append(hex.toInt(16).toChar())
                                        i += 4
                                    }
                                }
                                else -> sb.append(esc)
                            }
                            i += 2
                        } else {
                            sb.append(content[i])
                            i++
                        }
                    }
                    if (i < content.length) i++ // skip closing quote
                    tokens.add(JsonToken(JsonTokenType.STRING, sb.toString()))
                }
                c == '-' || c in '0'..'9' -> {
                    val start = i
                    if (c == '-') i++
                    while (i < content.length && content[i] in '0'..'9') i++
                    tokens.add(JsonToken(JsonTokenType.NUMBER, content.substring(start, i)))
                }
                else -> i++ // skip unknown chars defensively
            }
        }
        return tokens
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
