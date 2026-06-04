package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import java.io.File
import java.security.MessageDigest

/**
 * Manages mutation history for incremental analysis.
 * Caches mutation results to avoid re-running analysis on unchanged code.
 */
class MutationHistoryManager(
    private val historyFile: File = File(".mutation-history")
) {

    /**
     * Data class for storing mutation history.
     */
    data class MutationHistory(
        val timestamp: Long = System.currentTimeMillis(),
        val classHashes: Map<String, String> = emptyMap(),  // className -> SHA-256 of bytecode
        val testHashes: Map<String, String> = emptyMap(),   // testName -> SHA-256 of bytecode
        val results: Map<String, MutationStatusResult> = emptyMap()  // mutationId -> result
    )

    /**
     * Data class for storing individual mutation results.
     */
    data class MutationStatusResult(
        val status: MutationStatus,
        val timestamp: Long = System.currentTimeMillis(),
        val executionTimeMs: Long = 0
    )

    /**
     * Load history from file.
     */
    fun load(): MutationHistory {
        if (!historyFile.exists()) {
            return MutationHistory()
        }

        return try {
            val content = historyFile.readText()
            parseHistory(content)
        } catch (e: Exception) {
            MutationHistory()
        }
    }

    /**
     * Save history to file.
     */
    fun save(history: MutationHistory) {
        try {
            val content = serializeHistory(history)
            historyFile.writeText(content)
        } catch (e: Exception) {
            // Ignore save errors
        }
    }

    /**
     * Compute SHA-256 hash of bytecode.
     */
    fun computeHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get reusable results from previous history.
     * Returns mutations that can be reused (class and test unchanged).
     */
    fun getReusableResults(
        classBytes: ByteArray,
        testBytes: ByteArray,
        oldHistory: MutationHistory
    ): Map<String, MutationStatus> {
        val classHash = computeHash(classBytes)
        val testHash = computeHash(testBytes)

        val reusableResults = mutableMapOf<String, MutationStatus>()

        for ((mutationId, result) in oldHistory.results) {
            // Check if this mutation belongs to the current class
            val parts = mutationId.split("_")
            if (parts.size >= 2) {
                val mutationClassHash = oldHistory.classHashes[parts[0]]
                val mutationTestHash = oldHistory.testHashes[parts[1]]

                // If class and test haven't changed, reuse the result
                if (mutationClassHash == classHash && mutationTestHash == testHash) {
                    reusableResults[mutationId] = result.status
                }
            }
        }

        return reusableResults
    }

    /**
     * Update history with new results.
     */
    fun updateHistory(
        oldHistory: MutationHistory,
        className: String,
        testName: String,
        classBytes: ByteArray,
        testBytes: ByteArray,
        newResults: Map<String, MutationStatus>
    ): MutationHistory {
        val classHash = computeHash(classBytes)
        val testHash = computeHash(testBytes)

        val updatedClassHashes = oldHistory.classHashes.toMutableMap()
        updatedClassHashes[className] = classHash

        val updatedTestHashes = oldHistory.testHashes.toMutableMap()
        updatedTestHashes[testName] = testHash

        val updatedResults = oldHistory.results.toMutableMap()
        for ((mutationId, status) in newResults) {
            updatedResults[mutationId] = MutationStatusResult(
                status = status,
                timestamp = System.currentTimeMillis()
            )
        }

        return MutationHistory(
            timestamp = System.currentTimeMillis(),
            classHashes = updatedClassHashes,
            testHashes = updatedTestHashes,
            results = updatedResults
        )
    }

    /**
     * Parse history from simple text format.
     * Format:
     * TIMESTAMP:<timestamp>
     * CLASS:<className>:<hash>
     * TEST:<testName>:<hash>
     * RESULT:<mutationId>:<status>:<timestamp>:<executionTimeMs>
     */
    private fun parseHistory(content: String): MutationHistory {
        val classHashes = mutableMapOf<String, String>()
        val testHashes = mutableMapOf<String, String>()
        val results = mutableMapOf<String, MutationStatusResult>()
        var timestamp = System.currentTimeMillis()

        for (line in content.lines()) {
            when {
                line.startsWith("TIMESTAMP:") -> {
                    timestamp = line.substringAfter(":").toLongOrNull() ?: System.currentTimeMillis()
                }
                line.startsWith("CLASS:") -> {
                    val parts = line.substringAfter(":").split(":")
                    if (parts.size >= 2) {
                        classHashes[parts[0]] = parts[1]
                    }
                }
                line.startsWith("TEST:") -> {
                    val parts = line.substringAfter(":").split(":")
                    if (parts.size >= 2) {
                        testHashes[parts[0]] = parts[1]
                    }
                }
                line.startsWith("RESULT:") -> {
                    val parts = line.substringAfter(":").split(":")
                    if (parts.size >= 4) {
                        val mutationId = parts[0]
                        val status = try {
                            MutationStatus.valueOf(parts[1])
                        } catch (e: Exception) {
                            MutationStatus.ERROR
                        }
                        val resultTimestamp = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                        val executionTimeMs = parts[3].toLongOrNull() ?: 0

                        results[mutationId] = MutationStatusResult(
                            status = status,
                            timestamp = resultTimestamp,
                            executionTimeMs = executionTimeMs
                        )
                    }
                }
            }
        }

        return MutationHistory(
            timestamp = timestamp,
            classHashes = classHashes,
            testHashes = testHashes,
            results = results
        )
    }

    /**
     * Serialize history to simple text format.
     */
    private fun serializeHistory(history: MutationHistory): String {
        return buildString {
            appendLine("TIMESTAMP:${history.timestamp}")

            for ((className, hash) in history.classHashes) {
                appendLine("CLASS:$className:$hash")
            }

            for ((testName, hash) in history.testHashes) {
                appendLine("TEST:$testName:$hash")
            }

            for ((mutationId, result) in history.results) {
                appendLine("RESULT:$mutationId:${result.status}:${result.timestamp}:${result.executionTimeMs}")
            }
        }
    }
}
