package com.github.rodrigotimoteo.mutation.model

/**
 * Status of a mutation test result.
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
    NO_COVERAGE
}