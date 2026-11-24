package com.github.rodrigotimoteo.mutkt.domain

/** All results of a run. */
public data class MutationReport(
    val results: List<MutantResult>,
) {
    /** (KILLED + TIMEOUT) / (total - ERROR) as a fraction; null when nothing is scorable (empty or only ERROR). */
    val score: Double? =
        results.count { it.status != MutantStatus.ERROR }.let { scorable ->
            val detected = results.count { it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMEOUT }
            if (scorable == 0) null else detected.toDouble() / scorable
        }

    // Lazy: eager construction would recurse into sub-reports forever.

    /** Sub-reports keyed by the mutated class name. */
    val byClass: Map<String, MutationReport> by lazy {
        results.groupBy { it.mutant.className }.mapValues { MutationReport(it.value) }
    }
}
