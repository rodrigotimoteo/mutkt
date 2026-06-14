package com.github.rodrigotimoteo.mutation.annotation

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS

/**
 * Configuration for mutation test execution.
 */
data class MutKtConfiguration(
    /**
     * Whether to require all mutations to be killed.
     */
    val mode: VerificationMode = VerificationMode.STRICT,
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
