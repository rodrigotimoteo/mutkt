package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Analyzes coverage data for coverage-guided mutation testing.
 *
 * When JaCoCo/Kover .exec files are available, mutations in uncovered code
 * can be skipped to save time. This analyzer provides the interface for
 * coverage-based filtering.
 *
 * Two modes:
 * 1. **Read mode**: Reads existing .exec files to determine coverage
 * 2. **Agent mode**: Instruments classes with JaCoCo agent for live coverage
 *
 * Usage in MutationEngine:
 * ```kotlin
 * val analyzer = CoverageAnalyzer()
 * if (coverageExecFile != null) {
 *     val filtered = analyzer.filterByCoverage(mutations, classFiles, coverageExecFile)
 *     // Only test mutations in covered code
 * }
 * ```
 */
class CoverageAnalyzer {
    private val logger = LoggerFactory.getLogger(CoverageAnalyzer::class.java)

    data class TestCoverage(
        val testClassName: String,
        val coveredLines: Set<Int>,
        val coveredMethods: Set<String>,
    )

    data class MutationCoverage(
        val mutation: MutationInfo,
        val coveringTests: List<String>,
    )

    /**
     * Loads execution data from a JaCoCo .exec file.
     *
     * @param execFile The .exec file to load
     * @return A simple wrapper indicating the file was loaded
     */
    fun loadExecutionData(execFile: File): CoverageData {
        if (!execFile.exists()) {
            logger.warn("Execution data file not found: ${execFile.absolutePath}")
            return CoverageData(empty = true)
        }

        return try {
            // Verify the file is readable and has content
            val size = execFile.length()
            if (size == 0L) {
                logger.warn("Execution data file is empty: ${execFile.absolutePath}")
                return CoverageData(empty = true)
            }

            logger.info("Loaded coverage data from ${execFile.absolutePath} ($size bytes)")
            CoverageData(empty = false, execFile = execFile)
        } catch (e: Exception) {
            logger.warn("Failed to load execution data from ${execFile.absolutePath}: ${e.message}")
            CoverageData(empty = true)
        }
    }

    /**
     * Analyzes coverage for mutations.
     *
     * NOTE: JaCoCo .exec parsing requires org.jacoco.core dependency.
     * Without it, all mutations are treated as covered (conservative).
     * Add org.jacoco:org.jacoco.core for proper coverage-based filtering.
     *
     * @param classBytes The class bytecode
     * @param className The fully qualified class name
     * @param coverageData The loaded coverage data
     * @param mutations The mutations to analyze
     * @return List of MutationCoverage with covering test information
     */
    fun analyzeCoverage(
        classBytes: ByteArray,
        className: String,
        coverageData: CoverageData,
        mutations: List<MutationInfo>,
    ): List<MutationCoverage> {
        if (coverageData.empty) {
            return mutations.map { MutationCoverage(it, listOf("all")) }
        }

        // JaCoCo exec parsing requires org.jacoco.core dependency
        // Without it, treat all mutations as covered (conservative)
        logger.info(
            "Coverage filtering: .exec file found but JaCoCo API not available — " +
                "treating all mutations as covered. Add org.jacoco:org.jacoco.core for proper filtering.",
        )
        return mutations.map { MutationCoverage(it, listOf("covered")) }
    }

    /**
     * Finds JaCoCo .exec files in standard locations.
     *
     * @param buildDir The project's build directory
     * @return List of found .exec files
     */
    fun findExecFiles(buildDir: File): List<File> {
        val possiblePaths =
            listOf(
                File(buildDir, "jacoco/test.exec"),
                File(buildDir, "jacoco/jacocoTestReport.exec"),
                File(buildDir, "reports/jacoco/test/jacocoTestReport.exec"),
            )

        return possiblePaths.filter { it.exists() }
    }

    /**
     * Simple wrapper for coverage data.
     */
    data class CoverageData(
        val empty: Boolean,
        val execFile: File? = null,
    )
}
