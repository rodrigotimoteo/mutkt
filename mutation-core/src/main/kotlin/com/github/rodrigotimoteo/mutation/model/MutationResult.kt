package com.github.rodrigotimoteo.mutation.model

/**
 * Result of testing a single mutation.
 *
 * Contains the mutation, its status after test execution, execution time,
 * and any error message if the mutation caused an error.
 *
 * @property mutation The mutation that was tested
 * @property status Status of the mutation (KILLED, SURVIVED, ERROR, TIMEOUT, NO_COVERAGE, WEAK_KILLED, SUBSUMED)
 * @property executionTimeMs Time taken to execute tests against this mutation
 * @property errorMessage Error message if status is ERROR
 * @see MutationStatus for status details
 */
data class MutationResult @JvmOverloads constructor(
    val mutation: Mutation,
    val status: MutationStatus,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null,
) {
    /**
     * True when the mutation was actually killed by a test, or killed
     * by a test side-effect (weak kill). Subsumed mutations are NOT
     * counted as killed: a SUBSUMED status means "redundant with
     * another mutation" — including it in the killed bucket would
     * inflate the kill rate and desync per-class scores from the
     * global `MutationReport.killedPercentage` (which only counts
     * KILLED). Use [MutationStatus.SUBSUMED] directly when you need
     * the full picture.
     */
    val isKilled: Boolean get() =
        status == MutationStatus.KILLED ||
            status == MutationStatus.WEAK_KILLED
    val isSurvived: Boolean get() = status == MutationStatus.SURVIVED
}

/**
 * Mutation score for a specific class.
 */
data class ClassMutationScore @JvmOverloads constructor(
    val className: String,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val weakMutations: Int = 0,
    val subsumedMutations: Int = 0,
    val score: Int = if (totalMutations > 0) (killedMutations * 100) / totalMutations else 0,
)

/**
 * Complete mutation testing report with results and statistics.
 *
 * The seven "counted" buckets ([killedMutations], [survivedMutations],
 * [errorMutations], [timeoutMutations], [noCoverageMutations],
 * [subsumedMutations], [weakKilledMutations]) always sum to
 * [totalMutations]. Subsumed and weak-killed mutations were previously
 * folded into `total` but lived in no bucket, so the displayed
 * percentages understated the survivor rate.
 */
data class MutationReport @JvmOverloads constructor(
    val results: List<MutationResult>,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val errorMutations: Int,
    val timeoutMutations: Int,
    val noCoverageMutations: Int,
    val totalExecutionTimeMs: Long,
    val subsumedMutations: Int = 0,
    val weakKilledMutations: Int = 0,
) {
    val killedPercentage: Int get() =
        if (totalMutations > 0) (killedMutations * 100) / totalMutations else 0

    val survivedPercentage: Int get() =
        if (totalMutations > 0) (survivedMutations * 100) / totalMutations else 0

    /**
     * Mutations removed by post-hoc subsumption analysis. Reported as a
     * separate bucket so the global and per-class reports use a single
     * scoring rule: the denominator includes subsumed mutations, the
     * numerator (killed / survived) excludes them.
     */
    val subsumedPercentage: Int get() =
        if (totalMutations > 0) (subsumedMutations * 100) / totalMutations else 0

    /** Mutation score (percentage of mutations killed). */
    val mutationScore: Int get() = killedPercentage

    /**
     * Cached per-class score breakdown, computed lazily on the first
     * [getClassScores] call. `by lazy` is the right primitive for a
     * `data class` cache: the cache is excluded from `copy` / `equals` /
     * `hashCode` / `toString` automatically, and the default
     * `LazyThreadSafetyMode.SYNCHRONIZED` makes the first call
     * race-safe without needing a `@Volatile` field.
     *
     * Scoring rule (matches the global report exactly): the denominator
     * is the class's total mutation count, INCLUDING subsumed and
     * weak-killed mutations. The numerator (`killedMutations`) is
     * status `KILLED` only — `WEAK_KILLED` is reported separately in
     * [ClassMutationScore.weakMutations] so the per-class row adds up
     * to the global report and the per-class kill rate stays
     * comparable to [killedPercentage]. The previous code used
     * [MutationResult.isKilled] (KILLED + WEAK_KILLED) for the
     * numerator, which made per-class scores higher than the global
     * score on the same data.
     */
    private val classScoresCache: List<ClassMutationScore> by lazy {
        results
            .groupBy { it.mutation.className }
            .map { (className, classResults) ->
                val total = classResults.size
                val subsumed = classResults.count { it.status == MutationStatus.SUBSUMED }
                val weakKilled = classResults.count { it.status == MutationStatus.WEAK_KILLED }
                ClassMutationScore(
                    className = className,
                    totalMutations = total,
                    killedMutations = classResults.count { it.status == MutationStatus.KILLED },
                    survivedMutations = classResults.count { it.isSurvived },
                    weakMutations = weakKilled,
                    subsumedMutations = subsumed,
                )
            }.sortedByDescending { it.score }
    }

    /** shields.io badge URL for mutation coverage. */
    val scoreBadgeUrl: String
        get() {
            val color =
                when {
                    mutationScore >= 80 -> "brightgreen"
                    mutationScore >= 60 -> "yellow"
                    mutationScore >= 40 -> "orange"
                    else -> "red"
                }
            return "https://img.shields.io/badge/mutation-$mutationScore%25-$color?style=flat"
        }

    /** shields.io badge markdown (for README). */
    val scoreBadgeMarkdown: String
        get() = "![Mutation Coverage]($scoreBadgeUrl)"

    /**
     * Get per-class mutation scores.
     *
     * Result is computed once and cached: the report is an immutable
     * snapshot, so re-computing on every call wasted CPU in the HTML
     * and graph generators that read it multiple times.
     *
     * Scoring rule (matches the global report): the denominator is the
     * class's total mutation count, INCLUDING subsumed mutations.
     * Killed/survived are reported alongside the subsumed count so the
     * per-class row reproduces the global percentage without an
     * external lookup. The previous behaviour subtracted subsumed from
     * the class total, making per-class kill rates incomparable to the
     * global kill rate.
     */
    fun getClassScores(): List<ClassMutationScore> = classScoresCache
}
