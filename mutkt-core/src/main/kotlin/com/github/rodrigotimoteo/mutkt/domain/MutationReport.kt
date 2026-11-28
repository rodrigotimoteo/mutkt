package com.github.rodrigotimoteo.mutkt.domain

/**
 * All results of a run; [omitted] counts eligible mutants the per-class cap left unevaluated, [deadlineCap] is set
 * when timeoutMs cut the deadline the baseline asked for.
 */
public data class MutationReport(
    val results: List<MutantResult>,
    val omitted: Int = 0,
    val deadlineCap: DeadlineCap? = null,
) {
    /** (KILLED + TIMEOUT) / (total - ERROR) as a fraction; null when nothing is scorable (empty or only ERROR). */
    val score: Double? =
        results.count { it.status != MutantStatus.ERROR }.let { scorable ->
            val detected = results.count { it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMEOUT }
            if (scorable == 0) null else detected.toDouble() / scorable
        }

    /** How many results were taken from the last run's history. */
    val reused: Int = results.count { it.reused }

    // Lazy: eager construction would recurse into sub-reports forever.

    /** Sub-reports keyed by the mutated class name. */
    val byClass: Map<String, MutationReport> by lazy {
        results.groupBy { it.mutant.className }.mapValues { MutationReport(it.value) }
    }
}
