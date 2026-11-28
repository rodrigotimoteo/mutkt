package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus

/** Results of an earlier run, valid only while its [fingerprint] matches the current run's. */
public data class History(
    val fingerprint: String,
    val entries: List<HistoryEntry>,
) {
    private val byMutant = entries.associateBy { it.mutant }

    init {
        require(byMutant.size == entries.size) { "A mutant appears more than once in the history" }
    }

    /** The entry of [mutant] when the run's [fingerprint] and the mutant's [key] both match, else null. */
    public fun reusable(
        fingerprint: String,
        mutant: MutantId,
        key: String,
    ): HistoryEntry? = byMutant[mutant.toString()]?.takeIf { fingerprint == this.fingerprint && it.key == key }
}

/**
 * The [status] of [mutant] (its id text) when its key was [key]; [killedBy] and [killKind] as in MutantResult.
 * ERROR may be transient and NO_COVERAGE comes fresh from every baseline, so neither is kept.
 */
public data class HistoryEntry(
    val mutant: String,
    val key: String,
    val status: MutantStatus,
    val killedBy: String? = null,
    val killKind: KillKind? = null,
) {
    init {
        require(status in REUSABLE) { "Only $REUSABLE results are kept, not $status" }
        require(killedBy == null || status == MutantStatus.KILLED) { "killedBy is only set for KILLED, not $status" }
        require(killKind == null || status == MutantStatus.KILLED) { "killKind is only set for KILLED, not $status" }
    }
}

private val REUSABLE = setOf(MutantStatus.KILLED, MutantStatus.SURVIVED, MutantStatus.TIMEOUT)
