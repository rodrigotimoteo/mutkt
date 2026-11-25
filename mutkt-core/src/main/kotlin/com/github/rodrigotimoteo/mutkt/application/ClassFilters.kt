package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.Mutant

/**
 * Selects what gets mutated. Empty [includes] means every class; [excludes] always win.
 * [excludedMethods] holds exact method names or `fully.qualified.Class#method` entries.
 */
public data class ClassFilters(
    val includes: List<Glob> = emptyList(),
    // Empty on purpose: the one place for default excludes; generated-class excludes arrive with P5.
    val excludes: List<Glob> = emptyList(),
    val excludedMethods: Set<String> = emptySet(),
    val maxMutantsPerClass: Int? = null,
) {
    init {
        maxMutantsPerClass?.let { require(it > 0) { "maxMutantsPerClass must be positive: $it" } }
        excludedMethods.forEach(::validateMethodEntry)
    }

    /** True when [className] passes the include and exclude globs. */
    public fun acceptsClass(className: String): Boolean =
        (includes.isEmpty() || includes.any { it.matches(className) }) && excludes.none { it.matches(className) }

    /** True when the method is not excluded by plain name or by `Class#method`. */
    public fun acceptsMethod(
        className: String,
        methodName: String,
    ): Boolean = methodName !in excludedMethods && "$className#$methodName" !in excludedMethods

    /** Keeps the first [maxMutantsPerClass] mutants of each class, in the given order, and counts the rest. */
    internal fun limit(mutants: List<Mutant>): Limited {
        val max = maxMutantsPerClass ?: return Limited(mutants, 0)
        val counts = mutableMapOf<String, Int>()
        val kept =
            mutants.filter {
                val seen = (counts[it.className] ?: 0) + 1
                counts[it.className] = seen
                seen <= max
            }
        return Limited(kept, mutants.size - kept.size)
    }
}

/** The [kept] mutants of a capped run and how many were [omitted] by the cap. */
internal data class Limited(
    val kept: List<Mutant>,
    val omitted: Int,
)

private fun validateMethodEntry(entry: String) {
    require(entry.isNotBlank()) { "Excluded method must not be blank" }
    val parts = entry.split('#')
    require(parts.size <= 2 && parts.all { it.isNotBlank() }) {
        "Excluded method needs a class and a method: $entry"
    }
}
