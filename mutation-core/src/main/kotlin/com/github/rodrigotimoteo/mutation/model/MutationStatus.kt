package com.github.rodrigotimoteo.mutation.model

/**
 * Status of a mutation test result.
 *
 * Each mutation is classified into one of these statuses after test execution:
 * - [KILLED] - Test failed, mutant detected (good!)
 * - [SURVIVED] - Test passed, mutant survived (potential test gap)
 * - [ERROR] - Error during mutant execution
 * - [TIMEOUT] - Mutant execution timed out
 * - [NO_COVERAGE] - Mutant not covered by any test
 *
 * @see MutationResult
 * @see MutationReport
 */
enum class MutationStatus {
    /** Test failed - mutant detected and killed */
    KILLED,

    /** Test passed - mutant survived (potential test gap) */
    SURVIVED,

    /** Error during mutant execution */
    ERROR,

    /** Mutant timed out */
    TIMEOUT,

    /** Mutant not covered by any test */
    NO_COVERAGE,
}
