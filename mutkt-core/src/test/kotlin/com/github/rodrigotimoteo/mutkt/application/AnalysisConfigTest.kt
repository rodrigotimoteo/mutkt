package com.github.rodrigotimoteo.mutkt.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AnalysisConfigTest {
    @Test
    fun `valid config keeps its values`() {
        val config = AnalysisConfig(ClassFilters(), 5.seconds, 4)
        assertEquals(5.seconds, config.timeout)
        assertEquals(4, config.parallelism)
    }

    @Test
    fun `timeout must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { AnalysisConfig(ClassFilters(), Duration.ZERO, 1) }
        assertThrows(IllegalArgumentException::class.java) { AnalysisConfig(ClassFilters(), (-1).seconds, 1) }
    }

    @Test
    fun `parallelism must be at least one`() {
        assertThrows(IllegalArgumentException::class.java) { AnalysisConfig(ClassFilters(), 1.seconds, 0) }
    }
}
