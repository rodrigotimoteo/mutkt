package com.github.rodrigotimoteo.mutation.coverage

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import java.io.File

/**
 * Analyzes coverage data to map tests to mutations.
 * Used for coverage-guided test selection (only run tests that cover a mutation).
 *
 * Currently a pass-through stub. When Kover generates .exec files,
 * add JaCoCo dependency back to read them for real coverage filtering.
 */
class CoverageAnalyzer {
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
     * Placeholder for loading execution data.
     * Replace with JaCoCo's ExecFileLoader when implementing real coverage filtering.
     */
    fun loadExecutionData(execFile: File): Map<String, Any> {
        return emptyMap()
    }

    /**
     * Analyzes coverage for a specific class and maps mutations to covering tests.
     * Pass-through: treats all mutations as covered until real JaCoCo integration.
     */
    fun analyzeCoverage(
        classBytes: ByteArray,
        className: String,
        executionData: Map<String, Any>,
        mutations: List<MutationInfo>,
    ): List<MutationCoverage> {
        return mutations.map { MutationCoverage(it, listOf("all")) }
    }
}
