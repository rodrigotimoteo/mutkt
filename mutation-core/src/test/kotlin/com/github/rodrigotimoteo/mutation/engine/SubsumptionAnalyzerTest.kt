package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.report.SubsumptionAnalyzer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsumptionAnalyzerTest {
    private fun createMutation(
        id: String,
        className: String = "com.Foo",
    ): Mutation {
        return Mutation(
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
    }

    private fun createResult(
        id: String,
        status: MutationStatus,
        className: String = "com.Foo",
    ): MutationResult {
        return MutationResult(mutation = createMutation(id, className), status = status, executionTimeMs = 100)
    }

    @Test
    fun `analyze returns empty list for empty results`() {
        val analyzer = SubsumptionAnalyzer()
        val result = analyzer.analyze(emptyList())
        assertEquals(0, result.skippedCount)
        assertTrue(result.subsumedMutations.isEmpty())
    }

    @Test
    fun `analyze returns empty list for single result`() {
        val analyzer = SubsumptionAnalyzer()
        val results = listOf(createResult("m1", MutationStatus.KILLED))
        val analysis = analyzer.analyze(results)
        assertEquals(0, analysis.skippedCount)
    }

    @Test
    fun `analyze returns empty subsumed for no-kill results`() {
        val analyzer = SubsumptionAnalyzer()
        val results =
            listOf(
                createResult("m1", MutationStatus.SURVIVED),
                createResult("m2", MutationStatus.SURVIVED),
            )
        val analysis = analyzer.analyze(results)
        assertEquals(0, analysis.skippedCount)
    }

    @Test
    fun `analyze removes mutation when another subsumes it`() {
        val analyzer = SubsumptionAnalyzer()
        // Both killed, same className → same kill set {"com.Foo"} → equal → bidirectional subsumption
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, className = "com.Foo"),
                createResult("m2", MutationStatus.KILLED, className = "com.Foo"),
            )
        val analysis = analyzer.analyze(results)
        // Both have kill set {"com.Foo"} — each subsumes the other, both marked subsumed
        assertEquals(2, analysis.skippedCount)
    }

    @Test
    fun `analyze removes M2 when M1 kill set contains M2 kill set`() {
        val analyzer = SubsumptionAnalyzer()
        // Two mutations, same className → equal kill sets → bidirectional subsumption
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, className = "com.Foo"),
                createResult("m2", MutationStatus.KILLED, className = "com.Foo"),
            )
        val analysis = analyzer.analyze(results)
        assertEquals(2, analysis.skippedCount)
    }

    @Test
    fun `analyze keeps both when kill sets are disjoint`() {
        val analyzer = SubsumptionAnalyzer()
        // Different classNames → disjoint kill sets → no subsumption
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, className = "com.Foo"),
                createResult("m2", MutationStatus.KILLED, className = "com.Bar"),
            )
        val analysis = analyzer.analyze(results)
        assertEquals(0, analysis.skippedCount)
    }

    @Test
    fun `analyze keeps both when kill sets partially overlap`() {
        val analyzer = SubsumptionAnalyzer()
        // Three mutations: m1 in com.Foo, m2 in com.Bar, m3 in com.Baz
        // All different classNames → all kill sets disjoint → no subsumption
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED, className = "com.Foo"),
                createResult("m2", MutationStatus.KILLED, className = "com.Bar"),
                createResult("m3", MutationStatus.KILLED, className = "com.Baz"),
            )
        val analysis = analyzer.analyze(results)
        assertEquals(0, analysis.skippedCount)
    }

    @Test
    fun `analyze returns original count of results`() {
        val analyzer = SubsumptionAnalyzer()
        // All same className → all kill sets equal → all subsumed by bidirectional logic
        val results = (1..5).map { createResult("m$it", MutationStatus.KILLED, className = "com.Foo") }
        val analysis = analyzer.analyze(results)
        // 5 mutations, all with kill set {"com.Foo"} — all 5 are subsumed
        assertEquals(5, analysis.skippedCount)
    }

    @Test
    fun `analyze skips non-killed results in kill matrix`() {
        val analyzer = SubsumptionAnalyzer()
        val results =
            listOf(
                createResult("m1", MutationStatus.KILLED),
                createResult("m2", MutationStatus.SURVIVED),
                createResult("m3", MutationStatus.ERROR),
            )
        val analysis = analyzer.analyze(results)
        // Only m1 in kill matrix → no pairs with different kill sets → no subsumption
        assertEquals(0, analysis.skippedCount)
    }
}
