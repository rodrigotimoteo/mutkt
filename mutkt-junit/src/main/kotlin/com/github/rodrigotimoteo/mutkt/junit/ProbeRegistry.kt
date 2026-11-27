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

/** What a probed class hit: the indexes of its site probes, and whether its entry slot (the last) was set. */
internal class ClassHits(
    val sites: List<Int>,
    val reached: Boolean,
)

/** What [className] hit since the last call, which is then cleared. */
internal fun drainProbes(className: String): ClassHits {
    val probes = arrays[className]?.takeIf { it.isNotEmpty() } ?: return ClassHits(emptyList(), false)
    val entry = probes.lastIndex
    val reached = probes[entry].also { probes[entry] = false }
    return ClassHits((0 until entry).filter { probes[it] }.onEach { probes[it] = false }, reached)
}
