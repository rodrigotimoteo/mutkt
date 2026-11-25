package com.github.rodrigotimoteo.mutkt.domain

/** A mutant and what happened to it; [detail] is a short cause, set for ERROR when one is known. */
public data class MutantResult(
    val mutant: Mutant,
    val status: MutantStatus,
    val detail: String? = null,
)
