package com.github.rodrigotimoteo.mutkt.junit.fork

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryLimitTest {
    private val mib = 1024L * 1024

    @Test
    fun `metaspace past 512 MiB or heap past 75 percent of its maximum is over the limit`() {
        assertFalse(MemoryUse(512 * mib, -1, 75, 100).overLimit)
        assertTrue(MemoryUse(512 * mib + 1, -1, 0, 100).overLimit)
        assertTrue(MemoryUse(0, -1, 76, 100).overLimit)
        assertFalse(MemoryUse(0, -1, 1000 * mib, -1).overLimit)
    }

    @Test
    fun `a small MaxMetaspaceSize lowers the metaspace limit to 75 percent of it`() {
        assertFalse(MemoryUse(75 * mib, 100 * mib, 0, 100).overLimit)
        assertTrue(MemoryUse(75 * mib + 1, 100 * mib, 0, 100).overLimit)
        assertFalse(MemoryUse(512 * mib, 2048 * mib, 0, 100).overLimit)
    }

    @Test
    fun `a reading over the limit is taken again after a collection`() {
        val readings = mutableListOf(MemoryUse(0, -1, 90, 100), MemoryUse(0, -1, 10, 100))
        assertFalse(needsRetirement { readings.removeFirst() })
        assertEquals(0, readings.size)
        assertTrue(needsRetirement { MemoryUse(0, -1, 90, 100) })
    }

    @Test
    fun `a reading within the limit needs no collection`() {
        var reads = 0
        assertFalse(needsRetirement { MemoryUse(0, -1, 10, 100).also { reads++ } })
        assertEquals(1, reads)
    }
}
