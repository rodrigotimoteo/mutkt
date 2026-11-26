package com.github.rodrigotimoteo.mutkt.junit.fork

import java.lang.management.ManagementFactory

private const val METASPACE_LIMIT_BYTES = 512L * 1024 * 1024
private const val LIMIT_PERCENT = 75
private const val PERCENT = 100

/** Used memory of a child JVM in bytes; [metaspaceMax] and [heapMax] are negative when there is no maximum. */
internal data class MemoryUse(
    val metaspace: Long,
    val metaspaceMax: Long,
    val heapUsed: Long,
    val heapMax: Long,
) {
    val overLimit: Boolean
        get() = metaspace > metaspaceLimit || (heapMax > 0 && heapUsed * PERCENT > heapMax * LIMIT_PERCENT)

    // A MaxMetaspaceSize at or below the fixed limit would end in an OutOfMemoryError before the limit is reached.
    private val metaspaceLimit: Long
        get() =
            when {
                metaspaceMax > 0 -> minOf(METASPACE_LIMIT_BYTES, metaspaceMax * LIMIT_PERCENT / PERCENT)
                else -> METASPACE_LIMIT_BYTES
            }
}

// A run's class loader stays reachable while a library thread or static holds it (connection pools, timers), so a
// reused child grows with every run. Collecting only when a reading is over the limit keeps the other runs fast.
// Under -XX:+DisableExplicitGC the second reading still counts garbage: children retire early, which costs only speed.
@Suppress("ExplicitGarbageCollectionCall")
internal fun needsRetirement(read: () -> MemoryUse = ::memoryUse): Boolean =
    read().overLimit &&
        run {
            System.gc()
            read().overLimit
        }

internal fun memoryUse(): MemoryUse {
    val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
    val metaspace = ManagementFactory.getMemoryPoolMXBeans().first { it.name == "Metaspace" }.usage
    return MemoryUse(metaspace.used, metaspace.max, heap.used, heap.max)
}
