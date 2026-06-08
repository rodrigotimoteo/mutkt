package com.github.rodrigotimoteo.mutation.baseline

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import java.io.File

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
    private val baselineDir = File(projectDir, ".mutkt")
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
        val lines = mutableListOf<String>()
        for ((className, mutations) in results) {
            for ((operator, line, status) in mutations) {
                lines.add("$className|$operator|$line|$status")
            }
        }
        baselineFile.writeText(lines.joinToString("\n"))
    }

    /**
     * Load previous baseline.
     *
     * @return Map of className to (operator, lineNumber, status) or empty map
     */
    fun load(): Map<String, List<Triple<String, Int, MutationStatus>>> {
        if (!baselineFile.exists()) return emptyMap()

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

        return results.mapValues { it.value.toList() }
    }

    /**
     * Detect changed classes using git diff.
     * Compares working tree against HEAD (works on initial commits, detached HEAD, etc.).
     *
     * @return Set of changed class names (dotted format)
     */
    fun detectChanges(): Set<String> {
        return try {
            val process =
                ProcessBuilder("git", "diff", "--name-only", "HEAD")
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                System.err.println("[MutKt] git diff failed (exit code $exitCode), treating all classes as changed")
                return emptySet()
            }

            output.lines()
                .filter { it.endsWith(".kt") || it.endsWith(".java") }
                .map { path ->
                    path.removeSuffix(".kt").removeSuffix(".java")
                        .replace("/", ".")
                        .replace("src.main.kotlin.", "")
                        .replace("src.main.java.", "")
                        .replace("src.main.", "")
                }
                .toSet()
        } catch (e: Exception) {
            System.err.println("[MutKt] git not available: ${e.message}, treating all classes as changed")
            emptySet()
        }
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
