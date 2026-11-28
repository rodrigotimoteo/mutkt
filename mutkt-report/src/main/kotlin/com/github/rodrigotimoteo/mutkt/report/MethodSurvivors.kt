package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus

/**
 * The survivors of one [method] (class, name and descriptor), by line. [coveredBy] joins their covering test classes
 * in first-seen order; null when one of them ran the whole suite.
 */
internal data class MethodSurvivors(
    val method: String,
    val survivors: List<MutantResult>,
    val coveredBy: List<String>?,
)

/** The survivors in [results] grouped by method, the method with the most survivors first. */
internal fun survivorsByMethod(results: List<MutantResult>): List<MethodSurvivors> =
    results
        .filter { it.status == MutantStatus.SURVIVED }
        .groupBy { it.mutant.run { Triple(className, methodName, methodDescriptor) } }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<Triple<String, String, String>, List<MutantResult>>> { it.value.size }
                .thenBy { it.key.first }
                .thenBy { it.key.second }
                .thenBy { it.key.third },
        ).map { (key, group) ->
            MethodSurvivors(
                "${key.first}.${key.second}${key.third}",
                group.sortedWith(compareBy({ it.mutant.line ?: Int.MAX_VALUE }, { it.mutant.id.toString() })),
                if (group.any { it.coveredBy == null }) null else group.flatMap { it.coveredBy.orEmpty() }.distinct(),
            )
        }
