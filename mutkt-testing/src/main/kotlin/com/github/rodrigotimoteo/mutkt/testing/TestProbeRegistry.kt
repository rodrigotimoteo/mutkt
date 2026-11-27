@file:JvmName("TestProbeRegistry")

package com.github.rodrigotimoteo.mutkt.testing

import java.util.concurrent.ConcurrentHashMap

/** Binary name of the probe registry instrumented classes reach through the system class loader in module tests. */
const val TEST_PROBE_REGISTRY = "com.github.rodrigotimoteo.mutkt.testing.TestProbeRegistry"

// Process-wide like the real registry: every loader that defines a probed class must reach the same arrays.
private val arrays = ConcurrentHashMap<String, BooleanArray>()

/** The probe array of [className] with [size] probes, new when the size changes; called by instrumented classes. */
fun probes(
    className: String,
    size: Int,
): BooleanArray =
    checkNotNull(arrays.compute(className) { _, old -> old?.takeIf { it.size == size } ?: BooleanArray(size) })

/** Forgets every array, so classes probed by an earlier test start with none hit. */
fun clearProbes() = arrays.clear()
