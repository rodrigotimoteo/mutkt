package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport

/** An undetected mutant as the baseline stores it: its [id] text and the digest of its [method]'s code. */
public data class Survivor(
    val id: String,
    val method: String,
)

/**
 * The survivors a team accepted. A survivor is accepted only when its id and its method's code both match: the id
 * names a site by its ordinal in the method, which a new site earlier in that method hands to another mutant.
 */
public data class SurvivorBaseline(
    val survivors: Set<Survivor>,
) {
    private val ids = survivors.mapTo(mutableSetOf()) { it.id }

    /** The [found] survivors this baseline does not accept, in their order. */
    public fun unaccepted(found: List<Survivor>): List<Survivor> = found.filterNot { it in survivors }

    /** Whether some survivor with [id] was accepted, whatever its method's code was then. */
    public fun accepted(id: String): Boolean = id in ids
}

/** The SURVIVED and NO_COVERAGE mutants of this report, each with the digest [method] gives for its method. */
public fun MutationReport.survivors(method: (Mutant) -> String): List<Survivor> =
    results
        .filter { it.status == MutantStatus.SURVIVED || it.status == MutantStatus.NO_COVERAGE }
        .map { Survivor(it.mutant.id.toString(), method(it.mutant)) }
