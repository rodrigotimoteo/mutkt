package com.github.rodrigotimoteo.mutation.cache

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import java.io.File
import java.io.RandomAccessFile
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
 * val status = cache.lookup(hash, "RETURN_VALS", "myMethod", 42, 0)
 * if (status != null) return status // skip re-testing
 * // ... run test ...
 * cache.store(hash, "RETURN_VALS", "myMethod", 42, 0, MutationStatus.KILLED)
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
     * @param methodName Method name containing the mutation
     * @param lineNumber Source line number
     * @param occurrenceIndex Index of the mutation occurrence within the line
     * @return Cached status or null if not found
     */
    fun lookup(
        classHash: String,
        operator: String,
        methodName: String,
        lineNumber: Int,
        occurrenceIndex: Int = 0,
    ): MutationStatus? =
        withFileLock(getCacheFile(classHash)) {
            val cacheFile = getCacheFile(classHash)
            if (!cacheFile.exists()) return@withFileLock null

            val key = "$operator:$methodName:$lineNumber:$occurrenceIndex"
            cacheFile.readLines()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.let { runCatching { MutationStatus.valueOf(it) }.getOrNull() }
        }

    /**
     * Store mutation result in cache.
     *
     * @param classHash SHA-256 hash of original class
     * @param operator Mutation operator name
     * @param methodName Method name containing the mutation
     * @param lineNumber Source line number
     * @param occurrenceIndex Index of the mutation occurrence within the line
     * @param status Test result status
     */
    fun store(
        classHash: String,
        operator: String,
        methodName: String,
        lineNumber: Int,
        occurrenceIndex: Int,
        status: MutationStatus,
    ) {
        val cacheFile = getCacheFile(classHash)
        withFileLock(cacheFile) {
            val key = "$operator:$methodName:$lineNumber:$occurrenceIndex"
            val entry = "$key=$status"

            val lines =
                if (cacheFile.exists()) {
                    cacheFile.readLines().filter { !it.startsWith("$key=") }
                } else {
                    emptyList()
                }

            cacheFile.writeText((lines + entry).joinToString("\n"))
        }
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
     *
     * Also sweeps any leftover `.lock` sidecar files from a previous
     * crash. Normal runs delete the lock in `withFileLock`'s `finally`
     * block, but a JVM kill mid-write can leave orphans; the next
     * `clear()` (or the next successful run) reclaims them.
     */
    fun clear() {
        cacheDir.walkTopDown()
            .filter { it.isFile && (it.extension == "cache" || it.name.endsWith(".lock")) }
            .forEach { it.delete() }
    }

    private fun getCacheFile(classHash: String): File {
        // Use first 8 chars as directory to avoid too many files in one dir
        val prefix = classHash.take(8)
        val dir = File(cacheDir, prefix)
        dir.mkdirs()
        return File(dir, "$classHash.cache")
    }

    /**
     * Run [block] while holding an exclusive OS file lock on `file.lock`.
     * Prevents concurrent Gradle workers from corrupting cache files.
     *
     * The `.lock` sidecar file is removed in `finally` so the file does
     * not accumulate in `.mutkt/cache/` over many runs. Removal is best-
     * effort: a crash mid-block may leave the lock behind, but a stale
     * `RandomAccessFile` lock is the actual correctness guarantee — the
     * sidecar file is only a placeholder. (`clear()` also sweeps any
     * leftovers from prior crashes.)
     */
    private inline fun <T> withFileLock(
        file: File,
        block: () -> T,
    ): T {
        val parent = file.parentFile
        if (parent != null) parent.mkdirs()
        val lockFile = File(parent, "${file.name}.lock")
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
