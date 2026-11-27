package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Probe

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

    /** The probes hit since the last call, which are then cleared. */
    fun drain(): Set<Probe> = classNames.flatMapTo(mutableSetOf()) { name -> drainProbes(name).map { Probe(name, it) } }
}
