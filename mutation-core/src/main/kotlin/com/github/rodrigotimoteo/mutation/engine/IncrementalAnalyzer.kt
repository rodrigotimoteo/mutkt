package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.analysis.GitChangeDetector
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Analyzes git changes to determine which classes need mutation testing.
 *
 * For incremental analysis, only mutations in classes affected by recent
 * changes are tested. This dramatically reduces test time on PRs.
 *
 * Usage:
 * ```kotlin
 * val analyzer = IncrementalAnalyzer(projectDir)
 * val changedClasses = analyzer.getChangedClasses()
 * val filteredMutations = allMutations.filter { it.first.className in changedClasses }
 * ```
 *
 * Modes:
 * - **git diff**: Compare working tree against HEAD (default)
 * - **branch diff**: Compare against a base branch (for PRs)
 *
 * The actual git subprocess and source-path-to-class-name mapping is
 * delegated to [GitChangeDetector] so the implementation stays in sync
 * with [com.github.rodrigotimoteo.mutation.baseline.BaselineStorage].
 */
class IncrementalAnalyzer(
    private val projectDir: File,
) {
    private val logger = LoggerFactory.getLogger(IncrementalAnalyzer::class.java)
    private val gitChangeDetector = GitChangeDetector(projectDir)

    /**
     * Gets the set of classes that have been modified.
     *
     * @param baseBranch The base branch to compare against (for PRs)
     * @param sourceDirs List of source directories to scan for .kt/.java files
     * @return Set of fully qualified class names that changed
     */
    fun getChangedClasses(
        baseBranch: String? = null,
        sourceDirs: List<String> = listOf("src/main/kotlin", "src/main/java"),
    ): Set<String> {
        val changedClasses = gitChangeDetector.detectChangedClasses(sourceDirs, baseBranch)
        if (changedClasses.isEmpty()) {
            logger.info("No changed files detected — running full mutation testing")
        } else {
            logger.info("Mapped to ${changedClasses.size} changed classes")
        }
        return changedClasses
    }

    /**
     * Checks if incremental analysis is available (git is present and project is a repo).
     */
    fun isAvailable(): Boolean = gitChangeDetector.isAvailable()
}
