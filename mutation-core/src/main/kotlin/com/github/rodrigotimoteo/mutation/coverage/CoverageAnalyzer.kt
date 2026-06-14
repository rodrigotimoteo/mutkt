package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
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
            return CoverageData.Empty
        }

        return try {
            val size = execFile.length()
            if (size == 0L) {
                logger.warn("Execution data file is empty: ${execFile.absolutePath}")
                return CoverageData.Empty
            }

            val loader = ExecFileLoader()
            loader.load(execFile)

            logger.info("Loaded coverage data from ${execFile.absolutePath} ($size bytes)")
            CoverageData.Valid(
                execFile = execFile,
                executionDataStore = loader.executionDataStore,
            )
        } catch (e: Exception) {
            logger.warn("Failed to load execution data from ${execFile.absolutePath}: ${e.message}")
            CoverageData.Empty
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
        when (coverageData) {
            is CoverageData.Empty -> return emptyMap()
            is CoverageData.Valid -> return analyzeCoveredLines(coverageData, classFiles)
        }
    }

    private fun analyzeCoveredLines(
        valid: CoverageData.Valid,
        classFiles: Map<String, ByteArray>,
    ): Map<String, Set<Int>> {
        return try {
            val coverageBuilder = CoverageBuilder()
            val analyzer = Analyzer(valid.executionDataStore, coverageBuilder)

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
                val firstLine = classCoverage.getFirstLine()
                val lastLine = classCoverage.getLastLine()
                if (firstLine > 0 && lastLine > 0) {
                    for (lineNr in firstLine..lastLine) {
                        val line = classCoverage.getLine(lineNr)
                        if (line != null && line.getStatus() != ICounter.NOT_COVERED) {
                            coveredLines.add(lineNr)
                        }
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
        when (coverageData) {
            is CoverageData.Empty -> return emptySet()
            is CoverageData.Valid -> return analyzeCoveredLinesForClass(coverageData, className, classBytes)
        }
    }

    private fun analyzeCoveredLinesForClass(
        valid: CoverageData.Valid,
        className: String,
        classBytes: ByteArray,
    ): Set<Int> {
        return try {
            val coverageBuilder = CoverageBuilder()
            val analyzer = Analyzer(valid.executionDataStore, coverageBuilder)
            analyzer.analyzeAll(classBytes.inputStream(), className)

            var coveredLines = emptySet<Int>()
            for (classCoverage in coverageBuilder.getClasses()) {
                if (classCoverage.getName() == className) {
                    val firstLine = classCoverage.getFirstLine()
                    val lastLine = classCoverage.getLastLine()
                    if (firstLine > 0 && lastLine > 0) {
                        val lines = mutableSetOf<Int>()
                        for (lineNr in firstLine..lastLine) {
                            val line = classCoverage.getLine(lineNr)
                            if (line != null && line.getStatus() != ICounter.NOT_COVERED) {
                                lines.add(lineNr)
                            }
                        }
                        coveredLines = lines
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
     * Analyzes coverage for mutations against loaded coverage data.
     *
     * Callers must handle [CoverageData.Empty] before invoking this method
     * so the invalid state is unrepresentable at the call site.
     */
    fun analyzeCoverage(
        classBytes: ByteArray,
        className: String,
        coverageData: CoverageData.Valid,
        mutations: List<MutationInfo>,
    ): List<MutationCoverage> {
        val coveredLines =
            getCoveredLinesForClass(
                coverageData.execFile,
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
     * Coverage data loaded from a JaCoCo .exec file.
     *
     * The invalid state (missing file, empty file, parse failure) is
     * represented as [CoverageData.Empty] rather than nullable fields,
     * so callers can exhaustively pattern-match on the type.
     */
    sealed interface CoverageData {
        data object Empty : CoverageData

        data class Valid(
            val execFile: File,
            val executionDataStore: ExecutionDataStore,
        ) : CoverageData
    }
}
