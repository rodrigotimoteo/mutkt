package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOrderingStrategyTest {
    private fun createMutationResult(
        id: String,
        className: String,
        status: MutationStatus,
    ): MutationResult {
        val mutation =
            Mutation(
                id = id,
                className = className,
                methodName = "testMethod",
                methodDescriptor = "()V",
                operator = MutationOperator.ARITHMETIC,
                lineNumber = 10,
                originalBytecode = ByteArray(0),
                mutatedBytecode = ByteArray(0),
                description = "test",
            )
        return MutationResult(mutation = mutation, status = status, executionTimeMs = 100)
    }

    @Test
    fun `orderTests sorts by kill count descending`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 5),
                "TestB" to TestOrderingStrategy.TestKillCount("TestB", 10),
                "TestC" to TestOrderingStrategy.TestKillCount("TestC", 2),
            )
        val ordered = strategy.orderTests(listOf("TestA", "TestB", "TestC"), history)
        assertEquals(listOf("TestB", "TestA", "TestC"), ordered)
    }

    @Test
    fun `orderTests puts tests with zero kills at the end`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 5),
                "TestB" to TestOrderingStrategy.TestKillCount("TestB", 0),
            )
        val ordered = strategy.orderTests(listOf("TestA", "TestB"), history)
        assertEquals(listOf("TestA", "TestB"), ordered)
    }

    @Test
    fun `orderTests with empty history keeps original order for zero kills`() {
        val strategy = TestOrderingStrategy()
        val ordered = strategy.orderTests(listOf("TestA", "TestB", "TestC"), emptyMap())
        assertEquals(listOf("TestA", "TestB", "TestC"), ordered)
    }

    @Test
    fun `getTopK returns the K most-likely-killing tests`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 5),
                "TestB" to TestOrderingStrategy.TestKillCount("TestB", 10),
                "TestC" to TestOrderingStrategy.TestKillCount("TestC", 2),
                "TestD" to TestOrderingStrategy.TestKillCount("TestD", 8),
            )
        val top = strategy.getTopKTests(listOf("TestA", "TestB", "TestC", "TestD"), history, k = 2)
        assertEquals(listOf("TestB", "TestD"), top)
    }

    @Test
    fun `getTopK with K greater than available tests returns all`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 5),
            )
        val top = strategy.getTopKTests(listOf("TestA"), history, k = 10)
        assertEquals(1, top.size)
        assertEquals("TestA", top[0])
    }

    @Test
    fun `updateHistory increments kill counts for KILLED results`() {
        val strategy = TestOrderingStrategy()
        val results =
            listOf(
                createMutationResult("m1", "com.FooTest", MutationStatus.KILLED),
                createMutationResult("m2", "com.FooTest", MutationStatus.KILLED),
                createMutationResult("m3", "com.FooTest", MutationStatus.SURVIVED),
            )

        val updated = strategy.updateHistory(emptyMap(), results)
        assertEquals(1, updated.size)
        assertEquals(2, updated["com.FooTest"]?.killCount)
    }

    @Test
    fun `updateHistory accumulates across multiple calls`() {
        val strategy = TestOrderingStrategy()
        val round1 =
            listOf(
                createMutationResult("m1", "com.FooTest", MutationStatus.KILLED),
            )
        val round2 =
            listOf(
                createMutationResult("m2", "com.FooTest", MutationStatus.KILLED),
            )

        val afterRound1 = strategy.updateHistory(emptyMap(), round1)
        val afterRound2 = strategy.updateHistory(afterRound1, round2)

        assertEquals(2, afterRound2["com.FooTest"]?.killCount)
    }

    @Test
    fun `updateHistory ignores non-KILLED results`() {
        val strategy = TestOrderingStrategy()
        val results =
            listOf(
                createMutationResult("m1", "com.FooTest", MutationStatus.SURVIVED),
                createMutationResult("m2", "com.FooTest", MutationStatus.ERROR),
                createMutationResult("m3", "com.FooTest", MutationStatus.TIMEOUT),
            )

        val updated = strategy.updateHistory(emptyMap(), results)
        assertEquals(0, updated.size)
    }

    @Test
    fun `getKillPercentages calculates correct percentages`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 3),
                "TestB" to TestOrderingStrategy.TestKillCount("TestB", 7),
            )

        val percentages = strategy.getKillPercentages(history)
        assertEquals(30.0, percentages["TestA"])
        assertEquals(70.0, percentages["TestB"])
    }

    @Test
    fun `getKillPercentages returns empty map when total kills is zero`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 0),
            )

        val percentages = strategy.getKillPercentages(history)
        assertTrue(percentages.isEmpty())
    }

    @Test
    fun `orderTests with all equal kill counts preserves insertion order`() {
        val strategy = TestOrderingStrategy()
        val history =
            mapOf(
                "TestA" to TestOrderingStrategy.TestKillCount("TestA", 0),
                "TestB" to TestOrderingStrategy.TestKillCount("TestB", 0),
                "TestC" to TestOrderingStrategy.TestKillCount("TestC", 0),
            )
        val ordered = strategy.orderTests(listOf("TestC", "TestA", "TestB"), history)
        assertEquals(listOf("TestC", "TestA", "TestB"), ordered)
    }
}
