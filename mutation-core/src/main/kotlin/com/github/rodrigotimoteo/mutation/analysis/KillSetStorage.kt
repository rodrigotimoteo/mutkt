package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.MUTKT_DIR
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Persists kill sets (which tests killed which mutations) across runs.
 *
 * Kill sets are used by [SubsumptionAnalyzer] for pre-test subsumption:
 * historical kill sets identify likely-subsumed mutants before testing.
 *
 * Storage format: `.mutkt/kill-sets.txt`
 * One line per mutation: `mutationId=test1,test2,test3`
 */
class KillSetStorage(private val projectDir: File) {
    private val logger = LoggerFactory.getLogger(KillSetStorage::class.java)
    private val storageDir = File(projectDir, MUTKT_DIR)
    private val storageFile = File(storageDir, "kill-sets.txt")

    init {
        storageDir.mkdirs()
    }

    /**
     * Load historical kill sets from disk.
     *
     * @return Map of mutation ID to set of test class names that killed it,
     *         or empty map if no history exists
     */
    fun load(): Map<String, Set<String>> {
        if (!storageFile.exists()) return emptyMap()

        return try {
            storageFile.readLines()
                .filter { it.isNotBlank() && "=" in it }
                .associate { line ->
                    val (id, testsPart) = line.split("=", limit = 2)
                    val tests = if (testsPart.isBlank()) emptySet() else testsPart.split(",").toSet()
                    id to tests
                }
        } catch (e: Exception) {
            logger.warn("Failed to load kill sets: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Save kill sets to disk.
     *
     * @param killSets Map of mutation ID to set of test class names that killed it
     */
    fun save(killSets: Map<String, Set<String>>) {
        try {
            storageFile.writeText(
                killSets.entries
                    .sortedBy { it.key }
                    .joinToString("\n") { (id, tests) ->
                        "$id=${tests.joinToString(",")}"
                    },
            )
        } catch (e: Exception) {
            logger.warn("Failed to save kill sets: ${e.message}")
        }
    }

    /**
     * Save with merge — preserves historical data for mutations not in current run.
     * Used by incremental analysis to avoid losing kill set data.
     *
     * @param newKillSets New kill sets to merge into historical data
     */
    fun saveMerged(newKillSets: Map<String, Set<String>>) {
        val historical = load()
        save(merge(historical, newKillSets))
    }

    /**
     * Merge new kill sets with historical data.
     *
     * New data takes precedence. Historical data is preserved for mutations
     * not present in the new run.
     *
     * @param historical Existing kill sets from previous runs
     * @param newKillSets Kill sets from the current run
     * @return Merged kill sets
     */
    fun merge(
        historical: Map<String, Set<String>>,
        newKillSets: Map<String, Set<String>>,
    ): Map<String, Set<String>> {
        return historical + newKillSets
    }
}
