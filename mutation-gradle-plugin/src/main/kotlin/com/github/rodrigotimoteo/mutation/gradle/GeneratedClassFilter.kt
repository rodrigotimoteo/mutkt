package com.github.rodrigotimoteo.mutation.gradle

/**
 * Filters generated classes (R, BuildConfig, Hilt factories, Kotlin synthetics)
 * from mutation testing.
 *
 * Patterns use glob-style wildcards against the fully qualified dot-separated
 * class name. Supported forms:
 * - exact match — class name equals the pattern
 * - prefix — class name starts with the pattern (pattern ends with asterisk)
 * - suffix — class name ends with the pattern (pattern starts with asterisk)
 * - contains — class name contains the pattern (pattern wrapped in asterisks)
 * - nested — class name starts with the pattern (pattern ends with dollar-asterisk)
 *
 * The leading double-asterisk path-agnostic marker (e.g. star-star-slash-Foo)
 * found in many Android codegen pattern sets is matched literally by this
 * matcher; callers may pre-strip it from patterns that use it as a
 * path-agnostic marker.
 */
object GeneratedClassFilter {
    /**
     * Returns true when [className] matches any pattern in [patterns].
     * Matching is dot-against-dot; the class name is expected in
     * `com.example.Foo` form (not JVM-internal `com/example/Foo`).
     */
    fun shouldExclude(
        className: String,
        patterns: Set<String>,
    ): Boolean {
        val slashed = className.replace('.', '/')
        return patterns.any { pattern ->
            val normalized = pattern.removePrefix("**/")
            when {
                normalized.endsWith("\$*") -> slashed.contains(normalized.removeSuffix("*"))
                normalized.startsWith("*") && normalized.endsWith("*") ->
                    slashed.contains(normalized.drop(1).dropLast(1))
                normalized.endsWith("*") -> slashed.startsWith(normalized.dropLast(1))
                normalized.startsWith("*") -> slashed.endsWith(normalized.drop(1))
                else -> slashed == normalized || className == normalized
            }
        }
    }
}
