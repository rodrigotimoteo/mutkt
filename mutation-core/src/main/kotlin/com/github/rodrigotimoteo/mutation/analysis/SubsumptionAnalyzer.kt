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
     * Analyze mutations for subsumption.
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

        val subsumed = mutableSetOf<String>()

        // Group mutations by class+method (subsumption only within same method)
        val grouped = mutations.groupBy { "${it.className}::${it.methodName}" }

        for ((_, methodMutations) in grouped) {
            if (methodMutations.size < 2) continue

            // Only consider KILLED mutations for subsumption
            val killedMutations = methodMutations.filter { killSets[it.id]?.isNotEmpty() == true }

            for (i in killedMutations.indices) {
                if (killedMutations[i].id in subsumed) continue

                val killSetI = killSets[killedMutations[i].id] ?: continue

                for (j in killedMutations.indices) {
                    if (i == j) continue
                    if (killedMutations[j].id in subsumed) continue

                    val killSetJ = killSets[killedMutations[j].id] ?: continue

                    // If killSetJ ⊆ killSetI, then i subsumes j
                    if (killSetJ.isNotEmpty() && killSetI.containsAll(killSetJ)) {
                        subsumed.add(killedMutations[j].id)
                    }
                }
            }
        }

        val essential = mutations.filter { it.id !in subsumed }
        return essential to subsumed
    }
}
