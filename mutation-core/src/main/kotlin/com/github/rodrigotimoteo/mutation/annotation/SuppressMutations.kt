package com.github.rodrigotimoteo.mutation.annotation

/**
 * Suppresses mutation testing on annotated classes or functions.
 *
 * Usage:
 * ```
 * @SuppressMutations(reason = "Generated code")
 * class BuildConfig { }
 *
 * @SuppressMutations(operators = ["ARITHMETIC"], reason = "Trivial getter")
 * fun getName(): String = name
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuppressMutations(
    /**
     * Specific operators to suppress. Empty array = suppress all operators.
     */
    val operators: Array<String> = [],
    /**
     * Reason for suppression (for documentation/reporting).
     */
    val reason: String = "",
)
