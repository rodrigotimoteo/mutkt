package com.github.rodrigotimoteo.mutation.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TestStrengthOrderingTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var ordering: TestStrengthOrdering

    @BeforeEach
    fun setUp() {
        ordering = TestStrengthOrdering(tempDir)
    }

    @Test
    fun `orderTests returns original order when no history`() {
        val tests = listOf("TestA", "TestB", "TestC")
        val ordered = ordering.orderTests(tests)
        assertEquals(tests, ordered)
    }

    @Test
    fun `orderTests orders by kill count`() {
        ordering.recordResults("TestA", 10, 20)
        ordering.recordResults("TestB", 5, 20)
        ordering.recordResults("TestC", 15, 20)

        val tests = listOf("TestA", "TestB", "TestC")
        val ordered = ordering.orderTests(tests)

        assertEquals("TestC", ordered[0]) // 15 kills
        assertEquals("TestA", ordered[1]) // 10 kills
        assertEquals("TestB", ordered[2]) // 5 kills
    }

    @Test
    fun `recordResults accumulates over multiple runs`() {
        ordering.recordResults("TestA", 5, 10)
        ordering.recordResults("TestA", 3, 10)

        val score = ordering.getStrengthScore("TestA")
        // (5+3) / (10+10) = 8/20 = 0.4
        assertEquals(0.4, score, 0.01)
    }

    @Test
    fun `getStrengthScore returns 0 for unknown test`() {
        val score = ordering.getStrengthScore("UnknownTest")
        assertEquals(0.0, score)
    }

    @Test
    fun `getStrengthScore calculates correctly`() {
        ordering.recordResults("TestA", 8, 10)
        val score = ordering.getStrengthScore("TestA")
        assertEquals(0.8, score, 0.01)
    }

    @Test
    fun `getStats returns all entries`() {
        ordering.recordResults("TestA", 5, 10)
        ordering.recordResults("TestB", 3, 10)

        val stats = ordering.getStats()
        assertEquals(2, stats.size)
        assertTrue(stats.containsKey("TestA"), "expected TestA in stats, got: ${stats.keys}")
        assertTrue(stats.containsKey("TestB"), "expected TestB in stats, got: ${stats.keys}")
    }

    @Test
    fun `clear removes all history`() {
        ordering.recordResults("TestA", 5, 10)
        ordering.recordResults("TestB", 3, 10)

        ordering.clear()

        val stats = ordering.getStats()
        assertTrue(stats.isEmpty(), "expected stats empty after clear, got: $stats")
    }

    @Test
    fun `orderTests handles empty list`() {
        val ordered = ordering.orderTests(emptyList())
        assertTrue(ordered.isEmpty(), "ordering empty list should return empty list, got: $ordered")
    }

    @Test
    fun `orderTests preserves all tests`() {
        ordering.recordResults("TestA", 10, 20)
        ordering.recordResults("TestB", 5, 20)

        val tests = listOf("TestA", "TestB", "TestC")
        val ordered = ordering.orderTests(tests)

        assertEquals(3, ordered.size)
        assertTrue(ordered.containsAll(tests), "all input tests should be preserved, got: $ordered")
    }
}
