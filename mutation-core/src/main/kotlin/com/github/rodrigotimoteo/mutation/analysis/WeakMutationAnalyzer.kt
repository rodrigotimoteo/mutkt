package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo

/**
 * Analyzes mutations to determine if they are reachable based on coverage data.
 *
 * Weak mutation analysis skips mutations that are on lines or in classes
 * that were not executed by the test suite. This reduces the number of
 * mutants that need to be tested, improving performance.
 */
class WeakMutationAnalyzer {
    /**
     * Checks if a mutation is reachable based on covered lines.
     *
     * @param mutation The mutation to check
     * @param coveredLines Set of line numbers that were executed
     * @return true if the mutation's line is in the covered set
     */
    fun isReachable(
        mutation: MutationInfo,
        coveredLines: Set<Int>,
    ): Boolean {
        return mutation.lineNumber in coveredLines
    }

    /**
     * Checks if a class is reachable based on covered classes.
     *
     * @param className The class name to check
     * @param coveredClasses Set of class names that were executed
     * @return true if the class is in the covered set
     */
    fun isClassReachable(
        className: String,
        coveredClasses: Set<String>,
    ): Boolean {
        return className in coveredClasses ||
            className.replace('/', '.') in coveredClasses ||
            className.replace('.', '/') in coveredClasses
    }

    /**
     * Filters out mutations that are not on covered lines.
     *
     * @param mutations List of mutations to filter
     * @param coveredLinesMap Map of class name to set of covered line numbers
     * @return List of mutations that are on covered lines
     */
    fun filterUnreachable(
        mutations: List<MutationInfo>,
        coveredLinesMap: Map<String, Set<Int>>,
    ): List<MutationInfo> {
        if (coveredLinesMap.isEmpty()) {
            return mutations
        }
        return mutations.filter { mutation ->
            val classLines =
                coveredLinesMap[mutation.className]
                    ?: coveredLinesMap[mutation.className.replace('.', '/')]
            classLines != null && mutation.lineNumber in classLines
        }
    }

    /**
     * Filters out mutations in classes that were not covered.
     *
     * @param mutations List of mutations to filter
     * @param coveredClasses Set of class names that were executed
     * @return List of mutations in covered classes
     */
    fun filterUnreachableClasses(
        mutations: List<MutationInfo>,
        coveredClasses: Set<String>,
    ): List<MutationInfo> {
        if (coveredClasses.isEmpty()) {
            return mutations
        }
        return mutations.filter { mutation ->
            isClassReachable(mutation.className, coveredClasses)
        }
    }

    /**
     * Filters mutations by both line-level and class-level coverage.
     *
     * @param mutations List of mutations to filter
     * @param coveredLinesMap Map of class name to set of covered line numbers
     * @param coveredClasses Set of class names that were executed
     * @return List of mutations that pass both filters
     */
    fun filter(
        mutations: List<MutationInfo>,
        coveredLinesMap: Map<String, Set<Int>>,
        coveredClasses: Set<String>,
    ): List<MutationInfo> {
        val afterClassFilter = filterUnreachableClasses(mutations, coveredClasses)
        return filterUnreachable(afterClassFilter, coveredLinesMap)
    }
}
