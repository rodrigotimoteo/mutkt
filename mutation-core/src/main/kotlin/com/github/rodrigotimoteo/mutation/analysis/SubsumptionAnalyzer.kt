package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.model.Mutation

/**
 * Subsumption analyzer for identifying redundant mutations.
 *
 * When mutation A's kill set is a superset of mutation B's kill set,
 * killing A also kills B. This allows skipping B (subsumed) for speedup.
 *
 * Example: If mutation B is killed by tests {T1, T2} and mutation A
 * is killed by tests {T1, T2, T3}, then A subsumes B — killing A
 * guarantees B is also killed.
 *
 * Subsumption only applies to mutations within the same class and method.
 * Cross-class and cross-method subsumption is not considered.
 *
 * **Complexity**: the inner pair comparison is **O(m²)** where `m` is the
 * number of mutations within a (class, method) group. For projects with
 * very large methods (hundreds of mutations per method) this can dominate
 * analysis time. Mutations are pre-grouped by `className::methodName` so
 * the cost is bounded per-method, not global. If you routinely see large
 * `m` values, consider increasing the in-method mutation filter (operator
 * restrict, dead-code elimination) or pre-sorting mutations by line to
 * exit early on non-overlapping regions.
 *
 * @see <a href="https://pitest.org/quickstart/configuring/subsumption/">PITest subsumption</a>
 */
class SubsumptionAnalyzer {
    /**
     * Predict subsumed mutations using historical kill sets (pre-test).
     *
     * Uses kill sets from previous runs to identify mutations likely to be
     * subsumed before testing. This is a heuristic — actual subsumption is
     * verified post-test.
     *
     * @param mutations List of mutations to analyze
     * @param historicalKillSets Kill sets from previous runs
     * @return Set of mutation IDs likely to be subsumed (should be skipped)
     */
    fun predictSubsumed(
        mutations: List<Mutation>,
        historicalKillSets: Map<String, Set<String>>,
    ): Set<String> {
        if (mutations.size < 2 || historicalKillSets.isEmpty()) return emptySet()

        return findSubsumed(mutations) { id -> historicalKillSets[id] }
    }

    /**
     * Analyze mutations for subsumption using actual test results.
     *
     * @param mutations List of mutations to analyze
     * @param killSets Map of mutation ID to set of test class names that killed it
     * @return Pair of (essential mutations, subsumed mutation IDs)
     */
    fun analyze(
        mutations: List<Mutation>,
        killSets: Map<String, Set<String>>,
    ): Pair<List<Mutation>, Set<String>> {
        if (mutations.size < 2) return mutations to emptySet()

        val subsumed = findSubsumed(mutations) { id -> killSets[id]?.takeIf { it.isNotEmpty() } }

        val essential = mutations.filter { it.id !in subsumed }
        return essential to subsumed
    }

    /**
     * Compare kill sets for mutations within the same class+method group.
     *
     * For every pair (i, j) where i != j, mutation i "subsumes" j when
     * `killSet(j) ⊂ killSet(i)` (strict subset, non-empty). The returned
     * set contains the IDs of the subsumed side (j) of each pair.
     *
     * The [killSetProvider] abstracts over the source of kill sets
     * (historical vs. actual), so both [predictSubsumed] and [analyze]
     * share the comparison loop.
     */
    private fun findSubsumed(
        mutations: List<Mutation>,
        killSetProvider: (String) -> Set<String>?,
    ): Set<String> {
        val subsumed = mutableSetOf<String>()

        // Group mutations by class+method (subsumption only within same method)
        val grouped = mutations.groupBy { "${it.className}::${it.methodName}" }

        for ((_, methodMutations) in grouped) {
            if (methodMutations.size < 2) continue

            // Pre-compute kill sets once per mutation in the group; the
            // pair loop would otherwise call the provider twice per pair
            // (O(n²) provider calls). Sort by ascending kill-set size so
            // the larger set is `i` (the candidate subsumer) and the
            // smaller set is `j` (the candidate subsumed). Bail the pair
            // as soon as |J| >= |I| — a superset can never be subsumed
            // by a subset-or-equal set.
            data class Entry(
                val id: String,
                val killSet: Set<String>,
            )
            val entries =
                methodMutations
                    .mapNotNull { m ->
                        val ks = killSetProvider(m.id) ?: return@mapNotNull null
                        Entry(m.id, ks)
                    }.sortedBy { it.killSet.size }

            for (i in entries.indices) {
                val entryI = entries[i]
                if (entryI.id in subsumed) continue

                for (j in entries.indices) {
                    if (i == j) continue
                    val entryJ = entries[j]
                    if (entryJ.id in subsumed) continue
                    // |J| <= |I| by sort; if equal the strict-subset
                    // check below excludes them anyway. Bail when J
                    // catches up — every remaining j has |J| >= |I|,
                    // and a superset cannot be subsumed.
                    if (entryJ.killSet.size >= entryI.killSet.size) break
                    if (entryJ.killSet.isNotEmpty() &&
                        entryI.killSet != entryJ.killSet &&
                        entryI.killSet.containsAll(entryJ.killSet)
                    ) {
                        subsumed.add(entryJ.id)
                    }
                }
            }
        }

        return subsumed
    }
}
