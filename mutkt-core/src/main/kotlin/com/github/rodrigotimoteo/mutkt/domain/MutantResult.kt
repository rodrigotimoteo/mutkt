package com.github.rodrigotimoteo.mutkt.domain

/**
 * A mutant and what happened to it. [detail] is a short cause, set for ERROR when one is known; [killedBy] names
 * the first failing test or container observed, and only a KILLED mutant has one.
 */
public data class MutantResult(
    val mutant: Mutant,
    val status: MutantStatus,
    val detail: String? = null,
    val killedBy: String? = null,
) {
    init {
        require(killedBy == null || status == MutantStatus.KILLED) { "killedBy is only set for KILLED, not $status" }
    }
}
