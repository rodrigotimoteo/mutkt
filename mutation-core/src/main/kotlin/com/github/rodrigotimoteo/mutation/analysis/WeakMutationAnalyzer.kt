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
     * Not part of the production pipeline — kept internal for in-module
     * tests. The engine uses [filterUnreachable] for the actual filtering.
     */
    internal fun isReachable(
        mutation: MutationInfo,
        coveredLines: Set<Int>,
    ): Boolean {
        return mutation.lineNumber in coveredLines
    }

    /**
     * Checks if a class is reachable based on covered classes.
     *
     * Not part of the production pipeline — kept internal for in-module
     * tests. The engine uses [filterUnreachable] for the actual filtering.
     */
    internal fun isClassReachable(
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
     * Production entry point invoked by [com.github.rodrigotimoteo.mutation.engine.MutationEngine].
     *
     * When the class is in the covered set but the line number is
     * unknown (`<= 0`), the mutation is treated as reachable: we
     * cannot prove it is unreachable, and conservatively keeping it
     * matches PITest's behaviour for weakly-tracked mutations. Dropping
     * unknown-line mutations from a known-covered class would silently
     * shrink the test set for no accuracy gain.
     */
    fun filterUnreachable(
        mutations: List<MutationInfo>,
        coveredLinesMap: Map<String, Set<Int>>,
    ): List<MutationInfo> {
        if (coveredLinesMap.isEmpty()) {
            return mutations
        }
        return mutations.filter { mutation ->
            // Cache the slashed form once per mutation. The previous code
            // called `replace('.', '/')` for the fallback lookup; with
            // covered-line lookups running for every mutation, the per-call
            // allocation added up. Compute once, reuse for both lookups.
            val dottedName = mutation.className
            val slashedName = dottedName.replace('.', '/')
            val classLines =
                coveredLinesMap[dottedName] ?: coveredLinesMap[slashedName]
            classLines != null && (mutation.lineNumber <= 0 || mutation.lineNumber in classLines)
        }
    }

    /**
     * Filters out mutations in classes that were not covered.
     *
     * Not part of the production pipeline — kept internal for in-module
     * tests. The engine uses [filterUnreachable] for the actual filtering.
     */
    internal fun filterUnreachableClasses(
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
     * Not part of the production pipeline — kept internal for in-module
     * tests. The engine uses [filterUnreachable] for the actual filtering.
     */
    internal fun filter(
        mutations: List<MutationInfo>,
        coveredLinesMap: Map<String, Set<Int>>,
        coveredClasses: Set<String>,
    ): List<MutationInfo> {
        val afterClassFilter = filterUnreachableClasses(mutations, coveredClasses)
        return filterUnreachable(afterClassFilter, coveredLinesMap)
    }
}
