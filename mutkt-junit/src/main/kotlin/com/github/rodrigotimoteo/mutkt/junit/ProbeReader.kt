package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Probe

/** The site probes hit and the probed classes entered since the last drain. */
internal data class Drained(
    val hits: Set<Probe>,
    val reached: Set<String>,
)

/**
 * Reads and clears the probes of the instrumented [classNames] from the registry, whichever loader defined the class.
 * Hits left by an earlier run in this JVM are dropped first.
 */
internal class ProbeReader(
    private val classNames: Collection<String>,
) {
    init {
        classNames.forEach(::drainProbes)
    }

    /** What was hit since the last call, which is then cleared. */
    fun drain(): Drained {
        val hits = mutableSetOf<Probe>()
        val reached = mutableSetOf<String>()
        for (name in classNames) {
            val drained = drainProbes(name)
            drained.sites.mapTo(hits) { Probe(name, it) }
            if (drained.reached) reached += name
        }
        return Drained(hits, reached)
    }
}
