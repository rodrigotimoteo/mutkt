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

            // Only consider mutations with kill data available; the provider
            // returns null for "no kill data" so the loop skips them.
            val withKillData = methodMutations.filter { killSetProvider(it.id) != null }

            for (i in withKillData.indices) {
                if (withKillData[i].id in subsumed) continue

                val killSetI = killSetProvider(withKillData[i].id) ?: continue

                for (j in withKillData.indices) {
                    if (i == j) continue
                    if (withKillData[j].id in subsumed) continue

                    val killSetJ = killSetProvider(withKillData[j].id) ?: continue

                    // If killSetJ ⊆ killSetI and killSetJ is a strict subset, then i subsumes j
                    if (killSetJ.isNotEmpty() && killSetI != killSetJ && killSetI.containsAll(killSetJ)) {
                        subsumed.add(withKillData[j].id)
                    }
                }
            }
        }

        return subsumed
    }
}
