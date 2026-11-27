package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Probe
import sun.misc.Unsafe

/**
 * Reads and clears the probe arrays, kept in the static field [field], of the instrumented [classNames] that [loader]
 * already loaded. A class a test only linked must stay uninitialized: Field.get would run its `<clinit>` here, out of
 * order, and one that fails there breaks every later test. Unsafe reads a static without initializing the class, whose
 * array is then still null.
 */
internal class ProbeReader(
    private val loader: MutantClassLoader,
    classNames: Collection<String>,
    private val field: String,
) {
    private val unloaded = classNames.toMutableSet()
    private val located = mutableListOf<Slot>()

    /** The probes hit since the last call, which are then cleared. */
    fun drain(): Set<Probe> {
        locate()
        return located.flatMapTo(mutableSetOf()) { slot ->
            val probes = UNSAFE.getObject(slot.base, slot.offset) as BooleanArray? ?: return@flatMapTo emptyList()
            probes.indices.filter { probes[it] }.map {
                probes[it] = false
                Probe(slot.className, it)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun locate() {
        val names = unloaded.iterator()
        for (name in names) {
            val type = loader.loadedClass(name) ?: continue
            val probes = type.getDeclaredField(field)
            located += Slot(name, UNSAFE.staticFieldBase(probes), UNSAFE.staticFieldOffset(probes))
            names.remove()
        }
    }

    private class Slot(
        val className: String,
        val base: Any,
        val offset: Long,
    )
}

private val UNSAFE: Unsafe =
    Unsafe::class.java
        .getDeclaredField("theUnsafe")
        .apply { isAccessible = true }
        .get(null) as Unsafe
