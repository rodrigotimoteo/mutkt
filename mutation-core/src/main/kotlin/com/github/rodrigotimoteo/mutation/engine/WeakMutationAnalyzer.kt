package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation
import java.io.File
import java.nio.file.Files

/**
 * Analyzes weak mutations - mutations that never reach execution point.
 *
 * A weak mutant is one where the mutated line is never executed by any test.
 * Weak mutants can be skipped because they can never be killed.
 */
class WeakMutationAnalyzer {

    /**
     * Strength of a mutation.
     */
    enum class MutationStrength {
        STRONG,  // Reaches execution point
        WEAK     // Never reaches execution point
    }

    /**
     * Result of weak mutation analysis.
     */
    data class WeakAnalysisResult(
        val weakMutations: List<Mutation>,
        val strongMutations: List<Mutation>,
        val weakCount: Int,
        val strongCount: Int
    )

    /**
     * Analyze mutations for weakness.
     *
     * @param mutations List of mutations to analyze
     * @param coverageData JaCoCo coverage data (optional)
     * @return WeakAnalysisResult with weak and strong mutations
     */
    fun analyze(
        mutations: List<Mutation>,
        coverageData: File? = null
    ): WeakAnalysisResult {
        val weakMutations = mutableListOf<Mutation>()
        val strongMutations = mutableListOf<Mutation>()

        // Load coverage data if available
        val coveredLines = if (coverageData != null && coverageData.exists()) {
            loadCoverageData(coverageData)
        } else {
            // No coverage data - assume all mutations are strong
            emptyMap()
        }

        for (mutation in mutations) {
            if (isWeakMutation(mutation, coveredLines)) {
                weakMutations.add(mutation)
            } else {
                strongMutations.add(mutation)
            }
        }

        return WeakAnalysisResult(
            weakMutations = weakMutations,
            strongMutations = strongMutations,
            weakCount = weakMutations.size,
            strongCount = strongMutations.size
        )
    }

    /**
     * Check if a mutation is weak (never reaches execution point).
     */
    private fun isWeakMutation(
        mutation: Mutation,
        coveredLines: Map<String, Set<Int>>
    ): Boolean {
        // If no coverage data, assume strong
        if (coveredLines.isEmpty()) {
            return false
        }

        // Get covered lines for this class
        val classCoverage = coveredLines[mutation.className] ?: return false

        // Check if mutation line is covered
        return mutation.lineNumber !in classCoverage
    }

    /**
     * Load coverage data from JaCoCo .exec file.
     */
    private fun loadCoverageData(execFile: File): Map<String, Set<Int>> {
        val coverage = mutableMapOf<String, MutableSet<Int>>()

        try {
            // Parse JaCoCo exec file format
            // This is a simplified parser - real implementation would use JaCoCo API
            val lines = Files.readAllLines(execFile.toPath())
            var currentClass: String? = null

            for (line in lines) {
                when {
                    line.startsWith("CLASS:") -> {
                        currentClass = line.removePrefix("CLASS:")
                    }
                    line.startsWith("LINE:") && currentClass != null -> {
                        val lineNumber = line.removePrefix("LINE:").toIntOrNull()
                        if (lineNumber != null) {
                            coverage.getOrPut(currentClass!!) { mutableSetOf() }.add(lineNumber)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty coverage
            // This means all mutations will be considered strong
        }

        return coverage
    }

    /**
     * Get strength for a mutation.
     */
    fun getStrength(mutation: Mutation, coveredLines: Map<String, Set<Int>>): MutationStrength {
        return if (isWeakMutation(mutation, coveredLines)) {
            MutationStrength.WEAK
        } else {
            MutationStrength.STRONG
        }
    }
}
