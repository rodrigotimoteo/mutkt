package com.github.rodrigotimoteo.mutkt.domain

/** A mutant and what happened to it. */
public data class MutantResult(
    val mutant: Mutant,
    val status: MutantStatus,
)
