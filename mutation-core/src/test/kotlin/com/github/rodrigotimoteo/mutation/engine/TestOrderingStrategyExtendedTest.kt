package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOrderingStrategyExtendedTest {
    private val strategy = TestOrderingStrategy()

    @Test
    fun `getTopKTests returns all if k larger than list size`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 5),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 3),
            )
        val result = strategy.getTopKTests(listOf("Test1", "Test2"), history, k = 10)
        assertEquals(2, result.size)
    }

    @Test
    fun `getTopKTests returns 0 for k=0`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 5),
            )
        val result = strategy.getTopKTests(listOf("Test1"), history, k = 0)
        assertEquals(0, result.size)
    }

    @Test
    fun `getTopKTests with k=1 returns highest kill count`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 1),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 10),
                "Test3" to TestOrderingStrategy.TestKillCount("Test3", 5),
            )
        val result = strategy.getTopKTests(listOf("Test1", "Test2", "Test3"), history, k = 1)
        assertEquals(1, result.size)
        assertEquals("Test2", result.first())
    }

    @Test
    fun `getTopKTests with k=3 returns all in order`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 1),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 10),
                "Test3" to TestOrderingStrategy.TestKillCount("Test3", 5),
            )
        val result = strategy.getTopKTests(listOf("Test1", "Test2", "Test3"), history, k = 3)
        assertEquals(listOf("Test2", "Test3", "Test1"), result)
    }

    @Test
    fun `getTopKTests on empty list returns empty`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 5),
            )
        val result = strategy.getTopKTests(emptyList(), history, k = 5)
        assertEquals(0, result.size)
    }

    @Test
    fun `getTopKTests with empty history orders as-given`() {
        val result = strategy.getTopKTests(listOf("Test1", "Test2"), emptyMap(), k = 2)
        // Without history, all tests have 0 kills — order is stable
        assertEquals(listOf("Test1", "Test2"), result)
    }

    @Test
    fun `getKillPercentages returns empty for empty history`() {
        val result = strategy.getKillPercentages(emptyMap())
        assertEquals(0, result.size)
    }

    @Test
    fun `getKillPercentages returns empty for all-zero history`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 0),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 0),
            )
        val result = strategy.getKillPercentages(history)
        assertEquals(0, result.size)
    }

    @Test
    fun `getKillPercentages with single test returns 100 percent`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 5),
            )
        val result = strategy.getKillPercentages(history)
        assertEquals(100.0, result["Test1"]!!, 0.001)
    }

    @Test
    fun `getKillPercentages with two tests splits proportionally`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 1),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 3),
            )
        val result = strategy.getKillPercentages(history)
        // Total = 4, Test1 = 25%, Test2 = 75%
        assertEquals(25.0, result["Test1"]!!, 0.001)
        assertEquals(75.0, result["Test2"]!!, 0.001)
    }

    @Test
    fun `getKillPercentages with three tests sums to 100`() {
        val history =
            mapOf(
                "Test1" to TestOrderingStrategy.TestKillCount("Test1", 1),
                "Test2" to TestOrderingStrategy.TestKillCount("Test2", 2),
                "Test3" to TestOrderingStrategy.TestKillCount("Test3", 3),
            )
        val result = strategy.getKillPercentages(history)
        assertEquals(100.0, result.values.sum(), 0.001)
    }

    @Test
    fun `updateHistory ignores non-KILLED results`() {
        val results =
            listOf(
                createResult("com.Foo", MutationStatus.SURVIVED),
                createResult("com.Bar", MutationStatus.ERROR),
                createResult("com.Baz", MutationStatus.TIMEOUT),
            )
        val history = strategy.updateHistory(emptyMap(), results)
        assertEquals(0, history.size)
    }

    @Test
    fun `updateHistory counts KILLED results`() {
        val results =
            listOf(
                createResult("com.Foo", MutationStatus.KILLED),
                createResult("com.Foo", MutationStatus.KILLED),
                createResult("com.Bar", MutationStatus.KILLED),
            )
        val history = strategy.updateHistory(emptyMap(), results)
        assertEquals(2, history["com.Foo"]?.killCount)
        assertEquals(1, history["com.Bar"]?.killCount)
    }

    @Test
    fun `updateHistory increments existing counts`() {
        val initial =
            mapOf(
                "com.Foo" to TestOrderingStrategy.TestKillCount("com.Foo", 5),
            )
        val results =
            listOf(
                createResult("com.Foo", MutationStatus.KILLED),
                createResult("com.Foo", MutationStatus.KILLED),
            )
        val history = strategy.updateHistory(initial, results)
        assertEquals(7, history["com.Foo"]?.killCount)
    }

    @Test
    fun `updateHistory adds new tests to existing history`() {
        val initial =
            mapOf(
                "com.Foo" to TestOrderingStrategy.TestKillCount("com.Foo", 5),
            )
        val results =
            listOf(
                createResult("com.Bar", MutationStatus.KILLED),
            )
        val history = strategy.updateHistory(initial, results)
        assertEquals(5, history["com.Foo"]?.killCount)
        assertEquals(1, history["com.Bar"]?.killCount)
    }

    @Test
    fun `updateHistory returns new history (does not mutate input)`() {
        val initial =
            mapOf(
                "com.Foo" to TestOrderingStrategy.TestKillCount("com.Foo", 5),
            )
        val results =
            listOf(
                createResult("com.Foo", MutationStatus.KILLED),
            )
        val newHistory = strategy.updateHistory(initial, results)
        assertEquals(5, initial["com.Foo"]?.killCount, "Original history should be unchanged")
        assertEquals(6, newHistory["com.Foo"]?.killCount, "New history should have updated count")
    }

    @Test
    fun `orderTests handles tests not in history`() {
        val result =
            strategy.orderTests(
                listOf("Test1", "Test2", "Test3"),
                mapOf("Test2" to TestOrderingStrategy.TestKillCount("Test2", 10)),
            )
        // Test2 first (10), then Test1 and Test3 (0 each, stable order)
        assertEquals("Test2", result.first())
        assertTrue(result.contains("Test1"))
        assertTrue(result.contains("Test3"))
    }

    @Test
    fun `orderTests with negative kill count still works`() {
        val result =
            strategy.orderTests(
                listOf("Test1", "Test2"),
                mapOf(
                    "Test1" to TestOrderingStrategy.TestKillCount("Test1", 0),
                    "Test2" to TestOrderingStrategy.TestKillCount("Test2", -1),
                ),
            )
        assertEquals("Test1", result.first())
    }

    @Test
    fun `TestKillCount default lastRun is current time`() {
        val before = System.currentTimeMillis()
        val count = TestOrderingStrategy.TestKillCount("test", 5)
        val after = System.currentTimeMillis()
        assertTrue(count.lastRun in before..after)
    }

    @Test
    fun `TestKillCount equality and hashCode`() {
        val a = TestOrderingStrategy.TestKillCount("test", 5, 1000L)
        val b = TestOrderingStrategy.TestKillCount("test", 5, 1000L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    private fun createResult(
        className: String,
        status: MutationStatus,
    ): MutationResult {
        val mutation =
            Mutation(
                id = "m1",
                className = className,
                methodName = "test",
                methodDescriptor = "()V",
                operator = MutationOperator.ARITHMETIC,
                lineNumber = 10,
                originalBytecode = byteArrayOf(),
                mutatedBytecode = byteArrayOf(),
                description = "test",
            )
        return MutationResult(mutation, status, 50L)
    }
}
