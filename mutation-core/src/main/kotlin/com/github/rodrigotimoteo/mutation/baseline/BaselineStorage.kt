package com.github.rodrigotimoteo.mutation.baseline

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile

/**
 * Baseline storage for tracking mutation results across runs.
 *
 * Stores last run results in `.mutkt/baseline` and supports
 * git-based diff detection for incremental analysis.
 *
 * Features:
 * - File-based baseline storage (pipe-delimited text)
 * - Git diff detection for changed classes
 * - Baseline comparison for new/changed mutations
 *
 * Usage:
 * ```kotlin
 * val baseline = BaselineStorage(projectDir)
 * baseline.save(report)
 * val changed = baseline.detectChanges()
 * val diff = baseline.compareWithBaseline(currentResults)
 * ```
 */
class BaselineStorage(private val projectDir: File) {
    private val logger = LoggerFactory.getLogger(BaselineStorage::class.java)
    private val baselineDir = File(projectDir, MUTKT_DIR)
    private val baselineFile = File(baselineDir, "baseline")

    init {
        baselineDir.mkdirs()
    }

    /**
     * Save mutation report as baseline.
     *
     * @param results Map of className to (operator, lineNumber, status)
     */
    fun save(results: Map<String, List<Triple<String, Int, MutationStatus>>>) {
        withFileLock(baselineFile) {
            val lines = mutableListOf<String>()
            for ((className, mutations) in results) {
                for ((operator, line, status) in mutations) {
                    lines.add("$className|$operator|$line|$status")
                }
            }
            baselineFile.writeText(lines.joinToString("\n"))
        }
    }

    /**
     * Save with merge — preserves historical data for classes not in current run.
     * Used by incremental analysis to avoid losing baseline data.
     *
     * @param results New results to merge into baseline
     */
    fun saveMerged(results: Map<String, List<Triple<String, Int, MutationStatus>>>) {
        val existing = load().toMutableMap()
        for ((className, mutations) in results) {
            existing[className] = mutations
        }
        save(existing)
    }

    /**
     * Load previous baseline.
     *
     * @return Map of className to (operator, lineNumber, status) or empty map
     */
    fun load(): Map<String, List<Triple<String, Int, MutationStatus>>> {
        if (!baselineFile.exists()) return emptyMap()

        return withFileLock(baselineFile) {
            val results = mutableMapOf<String, MutableList<Triple<String, Int, MutationStatus>>>()

            for (line in baselineFile.readLines()) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                if (parts.size == 4) {
                    val className = parts[0]
                    val operator = parts[1]
                    val lineNumber = parts[2].toIntOrNull() ?: continue
                    val status = runCatching { MutationStatus.valueOf(parts[3]) }.getOrNull() ?: continue
                    results.getOrPut(className) { mutableListOf() }.add(Triple(operator, lineNumber, status))
                }
            }

            results.mapValues { it.value.toList() }
        }
    }

    /**
     * Detect changed classes using git diff.
     * Compares working tree against HEAD (works on initial commits, detached HEAD, etc.).
     *
     * @param sourceDirs Source directory prefixes to strip when converting
     *                   a path to a class name. Each entry is matched as a
     *                   `startsWith` check, so callers can use
     *                   `src/main/kotlin`, `src/main/java`,
     *                   `src/commonMain/kotlin`, or any custom layout. Files
     *                   outside every prefix are dropped (no class name can
     *                   be derived from them).
     * @return Set of changed class names (dotted format)
     */
    fun detectChanges(sourceDirs: List<String> = DEFAULT_SOURCE_DIRS): Set<String> {
        return try {
            val process =
                ProcessBuilder("git", "diff", "--name-only", "HEAD")
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.warn("git diff failed (exit code $exitCode), no incremental filtering applied")
                return emptySet()
            }

            output.lines()
                .filter { it.endsWith(".kt") || it.endsWith(".java") }
                .mapNotNull { path -> fileToClassName(path, sourceDirs) }
                .toSet()
        } catch (e: Exception) {
            logger.warn("git not available: ${e.message}, running all mutations (no filtering)")
            emptySet()
        }
    }

    /**
     * Convert a `src/.../Class.kt`-style path into a dotted class name by
     * stripping the first [sourceDirs] prefix that matches. Returns null
     * for files that do not live under any configured source root, so
     * callers can filter unknown layouts (e.g. generated resources) out
     * of the result.
     */
    private fun fileToClassName(
        path: String,
        sourceDirs: List<String>,
    ): String? {
        for (sourceDir in sourceDirs) {
            if (path.startsWith("$sourceDir/")) {
                val relative = path.removePrefix("$sourceDir/")
                val className =
                    relative
                        .removeSuffix(".kt")
                        .removeSuffix(".java")
                        .replace("/", ".")
                if (className.isNotEmpty()) return className
            }
        }
        return null
    }

    /**
     * Compare current results with baseline.
     *
     * @param currentResults Map of className to (operator, lineNumber, status)
     * @return DiffResult with new/changed/removed mutations
     */
    fun compareWithBaseline(currentResults: Map<String, List<Triple<String, Int, MutationStatus>>>): DiffResult {
        val baseline = load()

        val newMutations = mutableListOf<String>()
        val changedStatus = mutableListOf<Triple<String, String, MutationStatus>>()
        val removedMutations = mutableListOf<String>()

        // Find new and changed mutations
        for ((className, mutations) in currentResults) {
            val baselineMutations = baseline[className] ?: emptyList()
            val baselineMap = baselineMutations.associateBy { "${it.first}:${it.second}" }

            for ((operator, line, status) in mutations) {
                val key = "$operator:$line"
                val baselineStatus = baselineMap[key]
                if (baselineStatus == null) {
                    newMutations.add("$className:$key")
                } else if (baselineStatus.third != status) {
                    changedStatus.add(Triple(className, key, status))
                }
            }
        }

        // Find removed mutations
        for ((className, mutations) in baseline) {
            val currentMutations = currentResults[className] ?: emptyList()
            val currentMap = currentMutations.associateBy { "${it.first}:${it.second}" }

            for ((operator, line, _) in mutations) {
                val key = "$operator:$line"
                if (!currentMap.containsKey(key)) {
                    removedMutations.add("$className:$key")
                }
            }
        }

        return DiffResult(newMutations, changedStatus, removedMutations)
    }

    /**
     * Clear baseline data.
     */
    fun clear() {
        baselineFile.delete()
        // Sweep the .lock sidecar left over from `withFileLock` (deleted
        // on the happy path; this catches a crash mid-write). The lock
        // file lives next to baselineFile in `.mutkt/`.
        baselineFile.parentFile
            ?.listFiles { f -> f.name == "${baselineFile.name}.lock" }
            ?.forEach { it.delete() }
    }

    /**
     * Run [block] while holding an exclusive OS file lock on `file.lock`.
     * Prevents concurrent Gradle workers from corrupting baseline files.
     *
     * The `.lock` sidecar is removed in `finally` so it does not
     * accumulate in `.mutkt/` across runs. (`clear()` also sweeps any
     * orphans from a previous crash.)
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

    companion object {
        /**
         * Default source directory prefixes for the [detectChanges]
         * `src/main` → `src/main` mapping. The slashed form (not the
         * dotted form) is required because git reports paths with
         * forward slashes regardless of platform.
         */
        private val DEFAULT_SOURCE_DIRS: List<String> =
            listOf(
                "src/main/kotlin",
                "src/main/java",
                "src/commonMain/kotlin",
            )
    }
}

/**
 * Result of comparing current mutations with baseline.
 *
 * @property newMutations Mutations not in baseline (className:operator:line)
 * @property changedStatus Mutations with different status than baseline
 * @property removedMutations Mutations in baseline but not in current run
 */
data class DiffResult(
    val newMutations: List<String>,
    val changedStatus: List<Triple<String, String, MutationStatus>>,
    val removedMutations: List<String>,
) {
    val hasChanges: Boolean
        get() = newMutations.isNotEmpty() || changedStatus.isNotEmpty() || removedMutations.isNotEmpty()

    val summary: String
        get() =
            buildString {
                append("New: ${newMutations.size}, Changed: ${changedStatus.size}, Removed: ${removedMutations.size}")
            }
}
