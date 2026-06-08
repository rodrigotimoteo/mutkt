package com.github.rodrigotimoteo.mutation.engine

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
 * - **commit range**: Compare between two commits
 */
class IncrementalAnalyzer(
    private val projectDir: File,
) {
    private val logger = LoggerFactory.getLogger(IncrementalAnalyzer::class.java)

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
        val changedFiles = getChangedFiles(baseBranch)
        if (changedFiles.isEmpty()) {
            logger.info("No changed files detected — running full mutation testing")
            return emptySet()
        }

        logger.info("Found ${changedFiles.size} changed files")

        // Map changed files to class names
        val changedClasses = mutableSetOf<String>()
        for (file in changedFiles) {
            val className = fileToClassName(file, sourceDirs)
            if (className != null) {
                changedClasses.add(className)
                logger.debug("Changed class: $className (from $file)")
            }
        }

        logger.info("Mapped to ${changedClasses.size} changed classes")
        return changedClasses
    }

    /**
     * Gets changed files using git diff.
     *
     * @param baseBranch The base branch to compare against (null = working tree vs HEAD)
     * @return List of relative file paths that changed
     */
    private fun getChangedFiles(baseBranch: String? = null): List<String> {
        return try {
            val command =
                if (baseBranch != null) {
                    // Compare against base branch (for PRs)
                    listOf("git", "diff", "--name-only", "--diff-filter=ACMR", "origin/$baseBranch...HEAD")
                } else {
                    // Compare working tree against HEAD
                    listOf("git", "diff", "--name-only", "--diff-filter=ACMR", "HEAD")
                }

            val process =
                ProcessBuilder(command)
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.warn("Git diff failed with exit code $exitCode: $output")
                return emptyList()
            }

            output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && (it.endsWith(".kt") || it.endsWith(".java")) }
        } catch (e: Exception) {
            logger.warn("Failed to run git diff: ${e.message}")
            emptyList()
        }
    }

    /**
     * Converts a file path to a fully qualified class name.
     *
     * Example:
     * - "src/main/kotlin/com/example/MyClass.kt" → "com.example.MyClass"
     * - "src/main/java/com/example/MyClass.java" → "com.example.MyClass"
     */
    private fun fileToClassName(
        filePath: String,
        sourceDirs: List<String>,
    ): String? {
        for (sourceDir in sourceDirs) {
            if (filePath.startsWith("$sourceDir/")) {
                val relativePath = filePath.removePrefix("$sourceDir/")
                val className =
                    relativePath
                        .removeSuffix(".kt")
                        .removeSuffix(".java")
                        .replace("/", ".")

                // Validate it looks like a class name (non-empty, derived from source file)
                if (className.isNotEmpty()) {
                    return className
                }
            }
        }
        return null
    }

    /**
     * Checks if incremental analysis is available (git is present and project is a repo).
     */
    fun isAvailable(): Boolean {
        return try {
            val process =
                ProcessBuilder(listOf("git", "rev-parse", "--git-dir"))
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
