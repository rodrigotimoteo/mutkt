package com.github.rodrigotimoteo.mutation.annotation

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS

/**
 * Configuration for mutation test execution.
 *
 * Default mode is [VerificationMode.LENIENT] (not [VerificationMode.STRICT])
 * to match the @MutKtTest annotation default. The historical
 * `STRICT` default failed builds for any surviving mutation, which
 * was the wrong starting point for a new project with no test
 * coverage yet. Switch to `STRICT` once you have a known-good
 * baseline and want survivors to fail the build.
 */
data class MutKtConfiguration
    @JvmOverloads
    constructor(
        /**
         * Whether to require all mutations to be killed.
         */
        val mode: VerificationMode = VerificationMode.LENIENT,
        /**
         * Timeout in milliseconds for each mutant execution.
         */
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        /**
         * Whether to skip testing when running from IDE (single test).
         */
        val skipInIDE: Boolean = true,
        /**
         * Class name patterns to include for mutation.
         */
        val includePatterns: List<String> = emptyList(),
        /**
         * Class name patterns to exclude from mutation.
         */
        val excludePatterns: List<String> = listOf("**/*Test*", "**/*Generated*", "**/*Builder*"),
    )

/**
 * Verification mode for mutation testing.
 */
enum class VerificationMode {
    /**
     * All mutations must be killed. Any survivor fails the build.
     */
    STRICT,

    /**
     * Mutations are logged but failures don't fail the build.
     * Useful for CI to track mutation scores over time.
     */
    LENIENT,

    /**
     * Mutation testing is disabled. Equivalent to not using the annotation.
     */
    DISABLED,
}
