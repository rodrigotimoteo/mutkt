package com.github.rodrigotimoteo.mutation.cache

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import java.io.File
import java.security.MessageDigest

/**
 * File-based cache for mutation test results.
 *
 * Stores killed/survived status keyed by bytecode hash + operator.
 * Enables incremental testing by skipping previously killed mutants.
 *
 * Cache location: `.mutkt/cache/` in project root
 * File format: `{classHash}.cache` with one line per mutation
 *
 * Usage:
 * ```kotlin
 * val cache = MutKtCache(projectDir)
 * val hash = cache.computeClassHash(classBytes)
 * val status = cache.lookup(hash, "RETURN_VALS", 42)
 * if (status != null) return status // skip re-testing
 * // ... run test ...
 * cache.store(hash, "RETURN_VALS", 42, MutationStatus.KILLED)
 * ```
 */
class MutKtCache(private val projectDir: File) {
    private val cacheDir = File(projectDir, "$MUTKT_DIR/cache")

    init {
        cacheDir.mkdirs()
    }

    /**
     * Compute SHA-256 hash of class bytecode.
     *
     * @param classBytes Original class bytecode
     * @return Hex-encoded hash string
     */
    fun computeClassHash(classBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(classBytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Look up cached mutation status.
     *
     * @param classHash SHA-256 hash of original class
     * @param operator Mutation operator name
     * @param lineNumber Source line number
     * @return Cached status or null if not found
     */
    fun lookup(
        classHash: String,
        operator: String,
        lineNumber: Int,
    ): MutationStatus? {
        val cacheFile = getCacheFile(classHash)
        if (!cacheFile.exists()) return null

        val key = "$operator:$lineNumber"
        return cacheFile.readLines()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.let { runCatching { MutationStatus.valueOf(it) }.getOrNull() }
    }

    /**
     * Store mutation result in cache.
     *
     * @param classHash SHA-256 hash of original class
     * @param operator Mutation operator name
     * @param lineNumber Source line number
     * @param status Test result status
     */
    fun store(
        classHash: String,
        operator: String,
        lineNumber: Int,
        status: MutationStatus,
    ) {
        val cacheFile = getCacheFile(classHash)
        val key = "$operator:$lineNumber"
        val entry = "$key=$status"

        val lines =
            if (cacheFile.exists()) {
                cacheFile.readLines().filter { !it.startsWith("$key=") }
            } else {
                emptyList()
            }

        cacheFile.writeText((lines + entry).joinToString("\n"))
    }

    /**
     * Get cache statistics.
     *
     * @return Pair of (total entries, cache size in bytes)
     */
    fun stats(): Pair<Int, Long> {
        val files = cacheDir.walkTopDown().filter { it.isFile && it.extension == "cache" }.toList()
        val totalEntries = files.sumOf { it.readLines().size }
        val totalSize = files.sumOf { it.length() }
        return totalEntries to totalSize
    }

    /**
     * Clear all cached data.
     */
    fun clear() {
        cacheDir.walkTopDown().filter { it.isFile && it.extension == "cache" }.forEach { it.delete() }
    }

    private fun getCacheFile(classHash: String): File {
        // Use first 8 chars as directory to avoid too many files in one dir
        val prefix = classHash.take(8)
        val dir = File(cacheDir, prefix)
        dir.mkdirs()
        return File(dir, "$classHash.cache")
    }
}
