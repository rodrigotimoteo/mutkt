package com.github.rodrigotimoteo.mutkt.domain

/**
 * A mutant and what happened to it. [detail] is a short cause, set for ERROR when one is known; [killedBy] names
 * the first failing test or container observed, and only a KILLED mutant has one. [coveredBy] names the test classes
 * that reached the mutant, in the order they ran: null when it ran the whole suite, empty when no test ran. [reused]
 * when the status was taken from the last run's history instead of running the tests.
 */
public data class MutantResult(
    val mutant: Mutant,
    val status: MutantStatus,
    val detail: String? = null,
    val killedBy: String? = null,
    val coveredBy: List<String>? = null,
    val reused: Boolean = false,
) {
    init {
        require(killedBy == null || status == MutantStatus.KILLED) { "killedBy is only set for KILLED, not $status" }
    }
}
