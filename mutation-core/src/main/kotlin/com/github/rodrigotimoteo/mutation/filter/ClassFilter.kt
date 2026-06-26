package com.github.rodrigotimoteo.mutation.filter

/**
 * Filters classes for mutation testing using regex patterns.
 *
 * Supports target and exclude patterns for both classes and tests.
 *
 * @deprecated This class is no longer used in production. The engine now drives
 *   class selection through [com.github.rodrigotimoteo.mutation.engine.MutationEngine]'s
 *   `includePatterns` / `excludePatterns` (regex against the FQ class name) and the
 *   Gradle plugin's `GeneratedClassFilter` for codegen names. Retained only because
 *   `ClassFilterTest` still exercises it. Do not call from new code.
 */
@Deprecated(
    message =
        "Replaced by MutationEngine.includePatterns / excludePatterns; " +
            "production wiring lives in the Gradle plugin and engine.",
    replaceWith =
        ReplaceWith(
            "MutationEngine(includePatterns = ..., excludePatterns = ...)",
            "com.github.rodrigotimoteo.mutation.engine.MutationEngine",
        ),
)
class ClassFilter(
    private val targetPatterns: List<Regex> = emptyList(),
    private val excludePatterns: List<Regex> = emptyList(),
    private val targetTestPatterns: List<Regex> = emptyList(),
    private val excludeTestPatterns: List<Regex> = emptyList(),
) {
    /**
     * Check if a class should be mutated.
     *
     * @param className Fully qualified class name
     * @return true if the class should be mutated
     */
    fun shouldMutate(className: String): Boolean {
        return matchesTarget(className, targetPatterns, excludePatterns)
    }

    /**
     * Check if a test class should be run.
     *
     * @param testClassName Fully qualified test class name
     * @return true if the test class should be run
     */
    fun shouldRunTest(testClassName: String): Boolean {
        return matchesTarget(testClassName, targetTestPatterns, excludeTestPatterns)
    }

    /**
     * Check if a class matches target patterns and not exclude patterns.
     */
    private fun matchesTarget(
        name: String,
        targets: List<Regex>,
        excludes: List<Regex>,
    ): Boolean {
        // If no target patterns, match everything
        val matchesTarget = targets.isEmpty() || targets.any { it.matches(name) }

        // If matches any exclude pattern, exclude it
        val matchesExclude = excludes.any { it.matches(name) }

        return matchesTarget && !matchesExclude
    }

    /**
     * Create filter from string patterns.
     */
    companion object {
        /**
         * Create filter from target and exclude patterns.
         *
         * @param targetClasses Regex patterns for target classes
         * @param excludeClasses Regex patterns for excluded classes
         * @param targetTests Regex patterns for target tests
         * @param excludeTests Regex patterns for excluded tests
         */
        fun fromPatterns(
            targetClasses: List<String> = emptyList(),
            excludeClasses: List<String> = emptyList(),
            targetTests: List<String> = emptyList(),
            excludeTests: List<String> = emptyList(),
        ): ClassFilter {
            return ClassFilter(
                targetPatterns = targetClasses.map { Regex(it) },
                excludePatterns = excludeClasses.map { Regex(it) },
                targetTestPatterns = targetTests.map { Regex(it) },
                excludeTestPatterns = excludeTests.map { Regex(it) },
            )
        }

        /**
         * Create filter that matches everything (no filtering).
         */
        fun all(): ClassFilter {
            return ClassFilter()
        }
    }
}
