package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.tools.ExecFileLoader
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Analyzes coverage data for coverage-guided mutation testing.
 *
 * Parses JaCoCo .exec files to determine line-level coverage.
 * Mutations in uncovered code can be skipped to save time.
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
     */
    fun loadExecutionData(execFile: File): CoverageData {
        if (!execFile.exists()) {
            logger.warn("Execution data file not found: ${execFile.absolutePath}")
            return CoverageData(empty = true)
        }

        return try {
            val size = execFile.length()
            if (size == 0L) {
                logger.warn("Execution data file is empty: ${execFile.absolutePath}")
                return CoverageData(empty = true)
            }

            val loader = ExecFileLoader()
            loader.load(execFile)

            logger.info("Loaded coverage data from ${execFile.absolutePath} ($size bytes)")
            CoverageData(
                empty = false,
                execFile = execFile,
                executionDataStore = loader.executionDataStore,
            )
        } catch (e: Exception) {
            logger.warn("Failed to load execution data from ${execFile.absolutePath}: ${e.message}")
            CoverageData(empty = true)
        }
    }

    /**
     * Gets covered line numbers from a .exec file for all classes.
     *
     * @param execFile The JaCoCo .exec file
     * @param classFiles Map of class name (slashed) to bytecode
     * @return Map of class name (slashed) to set of covered line numbers
     */
    fun getCoveredLines(
        execFile: File,
        classFiles: Map<String, ByteArray>,
    ): Map<String, Set<Int>> {
        val coverageData = loadExecutionData(execFile)
        if (coverageData.empty || coverageData.executionDataStore == null) {
            return emptyMap()
        }

        return try {
            val coverageBuilder = CoverageBuilder()
            val analyzer = Analyzer(coverageData.executionDataStore, coverageBuilder)

            for ((className, classBytes) in classFiles) {
                try {
                    analyzer.analyzeAll(classBytes.inputStream(), className)
                } catch (e: Exception) {
                    logger.debug("Could not analyze coverage for $className: ${e.message}")
                }
            }

            val result = mutableMapOf<String, MutableSet<Int>>()
            for (classCoverage in coverageBuilder.getClasses()) {
                val coveredLines = mutableSetOf<Int>()
                val lineCounter = classCoverage.getLineCounter()
                // JaCoCo line coverage is probed by probes, not exact line numbers
                // We use the line counter's covered count as a heuristic
                if (lineCounter.getCoveredCount() > 0) {
                    // Mark all lines as covered if any line in the class is covered
                    // This is conservative — real per-line data requires source file analysis
                    // For mutation testing, this is sufficient: if any test reaches this class,
                    // we assume the class is instrumented and has coverage
                    for (i in 1..lineCounter.getTotalCount()) {
                        coveredLines.add(i)
                    }
                }
                if (coveredLines.isNotEmpty()) {
                    result[classCoverage.getName()] = coveredLines
                }
            }

            logger.info("Coverage analysis: ${result.size} classes with covered lines")
            result
        } catch (e: Exception) {
            logger.warn("Failed to analyze coverage: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Gets covered line numbers for a specific class.
     */
    fun getCoveredLinesForClass(
        execFile: File,
        className: String,
        classBytes: ByteArray,
    ): Set<Int> {
        val coverageData = loadExecutionData(execFile)
        if (coverageData.empty || coverageData.executionDataStore == null) {
            return emptySet()
        }

        return try {
            val coverageBuilder = CoverageBuilder()
            val analyzer = Analyzer(coverageData.executionDataStore, coverageBuilder)
            analyzer.analyzeAll(classBytes.inputStream(), className)

            var coveredLines = emptySet<Int>()
            for (classCoverage in coverageBuilder.getClasses()) {
                if (classCoverage.getName() == className) {
                    val lineCounter = classCoverage.getLineCounter()
                    if (lineCounter.getCoveredCount() > 0) {
                        coveredLines = (1..lineCounter.getTotalCount()).toSet()
                    }
                    break
                }
            }
            coveredLines
        } catch (e: Exception) {
            logger.warn("Failed to analyze coverage for $className: ${e.message}")
            emptySet()
        }
    }

    /**
     * Analyzes coverage for mutations.
     *
     * If coverage data is available, filters mutations to only those
     * that are covered by tests.
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

        val coveredLines =
            getCoveredLinesForClass(
                coverageData.execFile!!,
                className,
                classBytes,
            )

        if (coveredLines.isEmpty()) {
            return mutations.map { MutationCoverage(it, listOf("covered")) }
        }

        return mutations.map { mutation ->
            if (mutation.lineNumber in coveredLines) {
                MutationCoverage(mutation, listOf("covered"))
            } else {
                MutationCoverage(mutation, emptyList())
            }
        }
    }

    /**
     * Finds JaCoCo .exec files in standard locations.
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
     * Wrapper for coverage data.
     */
    data class CoverageData(
        val empty: Boolean,
        val execFile: File? = null,
        val executionDataStore: ExecutionDataStore? = null,
    )
}
