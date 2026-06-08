package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WeakMutationAnalyzerTest {
    private lateinit var analyzer: WeakMutationAnalyzer

    @BeforeEach
    fun setUp() {
        analyzer = WeakMutationAnalyzer()
    }

    @Test
    fun `filterReachable returns all mutations when no coverage data`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
                createMutationInfo(30),
            )

        val reachable = analyzer.filterReachable(mutations, emptySet())
        assertEquals(3, reachable.size)
    }

    @Test
    fun `filterReachable filters by covered lines`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
                createMutationInfo(30),
            )
        val coveredLines = setOf(10, 30)

        val reachable = analyzer.filterReachable(mutations, coveredLines)
        assertEquals(2, reachable.size)
        assertTrue(reachable.any { it.lineNumber == 10 })
        assertTrue(reachable.any { it.lineNumber == 30 })
    }

    @Test
    fun `filterReachable returns empty when no lines covered`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
            )
        val coveredLines = setOf(5, 15)

        val reachable = analyzer.filterReachable(mutations, coveredLines)
        assertTrue(reachable.isEmpty())
    }

    @Test
    fun `classifyByReachability splits correctly`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
                createMutationInfo(30),
            )
        val coveredLines = setOf(10, 30)

        val (reachable, unreachable) = analyzer.classifyByReachability(mutations, coveredLines)
        assertEquals(2, reachable.size)
        assertEquals(1, unreachable.size)
        assertTrue(unreachable.any { it.lineNumber == 20 })
    }

    @Test
    fun `classifyByReachability returns all reachable when no coverage`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
            )

        val (reachable, unreachable) = analyzer.classifyByReachability(mutations, emptySet())
        assertEquals(2, reachable.size)
        assertTrue(unreachable.isEmpty())
    }

    @Test
    fun `getStats calculates correctly`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
                createMutationInfo(30),
                createMutationInfo(40),
            )
        val coveredLines = setOf(10, 20)

        val stats = analyzer.getStats(mutations, coveredLines)
        assertEquals(4, stats.totalMutations)
        assertEquals(2, stats.reachableMutations)
        assertEquals(2, stats.unreachableMutations)
        assertEquals(50.0, stats.reachablePercentage, 0.01)
    }

    @Test
    fun `getStats returns 100 percent when all covered`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
            )
        val coveredLines = setOf(10, 20)

        val stats = analyzer.getStats(mutations, coveredLines)
        assertEquals(100.0, stats.reachablePercentage, 0.01)
    }

    @Test
    fun `getStats returns 0 percent when none covered`() {
        val mutations =
            listOf(
                createMutationInfo(10),
                createMutationInfo(20),
            )
        val coveredLines = setOf(5, 15)

        val stats = analyzer.getStats(mutations, coveredLines)
        assertEquals(0.0, stats.reachablePercentage, 0.01)
    }

    private fun createMutationInfo(lineNumber: Int): MutationInfo {
        return MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = "com.example.Foo",
            methodName = "bar",
            methodDescriptor = "()V",
            lineNumber = lineNumber,
            description = "Test mutation",
            // IADD
            originalOpcode = 96,
            // ISUB
            mutatedOpcode = 100,
        )
    }
}
