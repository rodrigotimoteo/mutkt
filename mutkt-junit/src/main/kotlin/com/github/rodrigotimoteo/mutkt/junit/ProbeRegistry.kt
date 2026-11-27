@file:JvmName("ProbeRegistry")

package com.github.rodrigotimoteo.mutkt.junit

import java.util.concurrent.ConcurrentHashMap

/** Binary name of the class holding [probes], the registry to give the coverage instrumenter. */
public const val PROBE_REGISTRY: String = "com.github.rodrigotimoteo.mutkt.junit.ProbeRegistry"

// Process-global by necessity: a sandbox loader (Robolectric's) defines the probed classes again, and every copy must
// reach the same array. Instrumented classes find it through the system class loader, which all of them share.
private val arrays = ConcurrentHashMap<String, BooleanArray>()

/**
 * The probe array of instrumented class [className], with [size] probes; instrumented classes call it reflectively.
 * A class probed again with another size (a later baseline in this JVM) gets a new array.
 */
public fun probes(
    className: String,
    size: Int,
): BooleanArray =
    checkNotNull(arrays.compute(className) { _, old -> old?.takeIf { it.size == size } ?: BooleanArray(size) })

/** The indexes of [className]'s probes hit since the last call, which are then cleared. */
internal fun drainProbes(className: String): List<Int> {
    val probes = arrays[className] ?: return emptyList()
    return probes.indices.filter { probes[it] }.onEach { probes[it] = false }
}
