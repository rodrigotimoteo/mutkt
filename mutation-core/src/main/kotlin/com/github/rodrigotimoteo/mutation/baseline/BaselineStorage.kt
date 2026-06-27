package com.github.rodrigotimoteo.mutation.baseline

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import com.github.rodrigotimoteo.mutation.analysis.GitChangeDetector
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
    private val gitChangeDetector = GitChangeDetector(projectDir)

    init {
        baselineDir.mkdirs()
    }

    /**
     * Save mutation report as baseline.
     *
     * The per-mutation tuple is a [BaselineEntry] (operator, lineNumber,
     * occurrenceIndex, status). The occurrence index disambiguates
     * multiple mutations that fall on the same source line with the
     * same operator — without it the baseline key collapses to
     * `(operator, line)` and the second mutation silently overwrites
     * the first.
     *
     * @param results Map of className to baseline entries
     */
    fun save(results: Map<String, List<BaselineEntry>>) {
        withFileLock(baselineFile) {
            val lines = mutableListOf<String>()
            for ((className, mutations) in results) {
                for (entry in mutations) {
                    lines.add("$className|${entry.operator}|${entry.lineNumber}|${entry.occurrenceIndex}|${entry.status}")
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
    fun saveMerged(results: Map<String, List<BaselineEntry>>) {
        val existing = load().toMutableMap()
        for ((className, mutations) in results) {
            existing[className] = mutations
        }
        save(existing)
    }

    /**
     * Load previous baseline.
     *
     * Two on-disk formats are accepted for backward compatibility:
     * - 5-part: `className|operator|line|occurrence|status` (current)
     * - 4-part: `className|operator|line|status` (legacy, treated as
     *   `occurrenceIndex = 0`)
     *
     * @return Map of className to baseline entries or empty map
     */
    fun load(): Map<String, List<BaselineEntry>> {
        if (!baselineFile.exists()) return emptyMap()

        return withFileLock(baselineFile) {
            val results = mutableMapOf<String, MutableList<BaselineEntry>>()

            for (line in baselineFile.readLines()) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                when (parts.size) {
                    5 -> {
                        val className = parts[0]
                        val operator = parts[1]
                        val lineNumber = parts[2].toIntOrNull() ?: continue
                        val occurrenceIndex = parts[3].toIntOrNull() ?: continue
                        val status = runCatching { MutationStatus.valueOf(parts[4]) }.getOrNull() ?: continue
                        results.getOrPut(className) { mutableListOf() }
                            .add(BaselineEntry(operator, lineNumber, occurrenceIndex, status))
                    }
                    4 -> {
                        // Legacy format without occurrenceIndex — treat
                        // as the first occurrence on that line.
                        val className = parts[0]
                        val operator = parts[1]
                        val lineNumber = parts[2].toIntOrNull() ?: continue
                        val status = runCatching { MutationStatus.valueOf(parts[3]) }.getOrNull() ?: continue
                        results.getOrPut(className) { mutableListOf() }
                            .add(BaselineEntry(operator, lineNumber, 0, status))
                    }
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
    fun detectChanges(sourceDirs: List<String> = GitChangeDetector.DEFAULT_SOURCE_DIRS): Set<String> =
        gitChangeDetector.detectChangedClasses(sourceDirs)

    /**
     * Compare current results with baseline.
     *
     * @param currentResults Map of className to baseline entries
     * @return DiffResult with new/changed/removed mutations
     */
    fun compareWithBaseline(currentResults: Map<String, List<BaselineEntry>>): DiffResult {
        val baseline = load()

        val newMutations = mutableListOf<String>()
        val changedStatus = mutableListOf<Triple<String, String, MutationStatus>>()
        val removedMutations = mutableListOf<String>()

        // Key includes the occurrence index so two mutations on the
        // same line with the same operator are tracked independently
        // — the previous `(operator, line)` key collapsed them.
        fun keyOf(entry: BaselineEntry) = "${entry.operator}:${entry.lineNumber}:${entry.occurrenceIndex}"

        // Find new and changed mutations
        for ((className, mutations) in currentResults) {
            val baselineMutations = baseline[className] ?: emptyList()
            val baselineMap = baselineMutations.associateBy { keyOf(it) }

            for (entry in mutations) {
                val key = keyOf(entry)
                val baselineStatus = baselineMap[key]
                if (baselineStatus == null) {
                    newMutations.add("$className:$key")
                } else if (baselineStatus.status != entry.status) {
                    changedStatus.add(Triple(className, key, entry.status))
                }
            }
        }

        // Find removed mutations
        for ((className, mutations) in baseline) {
            val currentMutations = currentResults[className] ?: emptyList()
            val currentMap = currentMutations.associateBy { keyOf(it) }

            for (entry in mutations) {
                val key = keyOf(entry)
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
}

/**
 * Result of comparing current mutations with baseline.
 *
 * @property newMutations Mutations not in baseline
 *   (className:operator:line:occurrenceIndex)
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

/**
 * Single mutation entry in the baseline file.
 *
 * Carries enough identity to disambiguate multiple mutations on the
 * same source line: the [occurrenceIndex] is the 0-based ordinal of the
 * mutation within the line (set by [com.github.rodrigotimoteo.mutation.mutator.MutationScanner]).
 * The previous `Triple<String, Int, MutationStatus>` key collapsed any
 * pair of same-line same-operator mutations into one row.
 *
 * @property operator Mutation operator name (e.g. `ARITHMETIC`)
 * @property lineNumber Source line number
 * @property occurrenceIndex 0-based index of the mutation on that line
 * @property status Last observed mutation status
 */
data class BaselineEntry(
    val operator: String,
    val lineNumber: Int,
    val occurrenceIndex: Int,
    val status: MutationStatus,
)
