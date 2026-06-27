package com.github.rodrigotimoteo.mutation.gradle

/**
 * Filters generated classes (R, BuildConfig, Hilt factories, Kotlin synthetics)
 * from mutation testing.
 *
 * Patterns are matched against the fully qualified dot-separated class name.
 * Single `*` matches zero or more characters within a single path segment
 * (does not cross `/`). Double `*` matches zero or more path segments,
 * including the leading double-star path-agnostic marker found in Android
 * codegen pattern sets. `?` matches a single character within a segment.
 *
 * Supported forms:
 * - exact — `R`, `BuildConfig` match only that simple class name
 * - prefix — `R$*` matches names starting with `R$`
 * - suffix — `*_Impl` matches names ending with `_Impl`
 * - contains — `*Hilt*` matches names containing `Hilt`
 * - nested — `R$*` matches `R$drawable`, `R$id`, etc.
 * - path-agnostic — a leading double-star with a trailing simple class
 *   name matches the simple name in any package.
 */
object GeneratedClassFilter {
    /**
     * Returns true when [className] matches any pattern in [patterns].
     * The class name is expected in `com.example.Foo` form (not
     * JVM-internal `com/example/Foo`).
     */
    fun shouldExclude(
        className: String,
        patterns: Set<String>,
    ): Boolean {
        val slashed = className.replace('.', '/')
        val simpleName = slashed.substringAfterLast('/')
        return patterns.any { pattern ->
            if (pattern.contains("**")) {
                matchesGlob(slashed, pattern)
            } else {
                matchesGlob(simpleName, pattern)
            }
        }
    }

    /**
     * Match a slashed path (segment-separated) against a glob pattern that
     * may contain `**`. The double-star matches zero or more path segments,
     * single `*` matches any chars within one segment (not crossing `/`),
     * and `?` matches a single character within a segment. Implementation
     * is delegated to the shared [globToRegex] helper to keep glob semantics
     * consistent with [MutationTask]'s excluded-class matcher.
     */
    private fun matchesGlob(
        path: String,
        glob: String,
    ): Boolean = Regex(globToRegex(glob)).matches(path)
}
