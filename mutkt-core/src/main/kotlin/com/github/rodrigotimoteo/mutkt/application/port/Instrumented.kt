package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.MutantId

/**
 * The classes that got probes (replacing the originals of the same name); each keeps its probes in the static
 * `boolean[]` field [probeField], null until the class first reaches one. [probes] locates each mapped mutant's.
 */
public class Instrumented(
    public val classes: List<ClassFile>,
    public val probeField: String,
    public val probes: Map<MutantId, Probe>,
)

/** Slot [index] of the probe array of class [className] (binary name). */
public data class Probe(
    val className: String,
    val index: Int,
)
