package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubsumptionAnalyzerTest {
    private lateinit var analyzer: SubsumptionAnalyzer

    @BeforeEach
    fun setUp() {
        analyzer = SubsumptionAnalyzer()
    }

    @Test
    fun `analyze returns all mutations when no subsumption`() {
        val mutations =
            listOf(
                createMutation("A", "method1", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 }),
                createMutation("B", "method1", 20, ByteArray(10) { 0x03 }, ByteArray(10) { 0x04 }),
            )
        val killSets =
            mapOf(
                mutations[0].id to setOf("TestA"),
                mutations[1].id to setOf("TestB"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze detects subsumption when kill set is subset`() {
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                // A kills more tests
                mutationA.id to setOf("Test1", "Test2", "Test3"),
                // B kills fewer → subsumed by A
                mutationB.id to setOf("Test1", "Test2"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // B is subsumed because killers(B) ⊆ killers(A)
        assertEquals(1, essential.size)
        assertEquals(mutationA.id, essential[0].id)
        assertEquals(1, subsumed.size)
        assertTrue(subsumed.contains(mutationB.id))
    }

    @Test
    fun `analyze does not subsume when kill sets are disjoint`() {
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                mutationA.id to setOf("Test1"),
                mutationB.id to setOf("Test2"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze does not subsume across different classes`() {
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Baz", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                mutationA.id to setOf("Test1", "Test2", "Test3"),
                mutationB.id to setOf("Test1", "Test2"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // No subsumption across different classes
        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze does not subsume across different methods`() {
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "baz", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                mutationA.id to setOf("Test1", "Test2", "Test3"),
                mutationB.id to setOf("Test1", "Test2"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // No subsumption across different methods
        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze handles single mutation`() {
        val mutation = createMutation("Foo", "bar", 10, ByteArray(5), ByteArray(5))
        val mutations = listOf(mutation)
        val killSets = mapOf(mutation.id to setOf("Test1"))

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        assertEquals(1, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze handles empty list`() {
        val (essential, subsumed) = analyzer.analyze(emptyList(), emptyMap())

        assertTrue(essential.isEmpty())
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze chains subsumption`() {
        // A kills {T1, T2, T3}, B kills {T1, T2}, C kills {T1}
        // A subsumes B, A subsumes C, B subsumes C
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })
        val mutationC = createMutation("Foo", "bar", 30, ByteArray(10) { 0x01 }, ByteArray(10) { 0x04 })

        val mutations = listOf(mutationA, mutationB, mutationC)
        val killSets =
            mapOf(
                mutationA.id to setOf("Test1", "Test2", "Test3"),
                mutationB.id to setOf("Test1", "Test2"),
                mutationC.id to setOf("Test1"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // Only A is essential
        assertEquals(1, essential.size)
        assertEquals(mutationA.id, essential[0].id)
        assertEquals(2, subsumed.size)
        assertTrue(subsumed.contains(mutationB.id))
        assertTrue(subsumed.contains(mutationC.id))
    }

    @Test
    fun `analyze does not subsume killed by unsurvived`() {
        // Only killed mutations can subsume
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                // A killed
                mutationA.id to setOf("Test1", "Test2"),
                // B not killed (no kill set entry)
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // B is not killed, so no subsumption possible
        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze handles same kill set`() {
        // Same kill set → each subsumes the other? No — only one should be essential
        val mutationA = createMutation("Foo", "bar", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 })
        val mutationB = createMutation("Foo", "bar", 20, ByteArray(10) { 0x01 }, ByteArray(10) { 0x03 })

        val mutations = listOf(mutationA, mutationB)
        val killSets =
            mapOf(
                mutationA.id to setOf("Test1", "Test2"),
                mutationB.id to setOf("Test1", "Test2"),
            )

        val (essential, subsumed) = analyzer.analyze(mutations, killSets)

        // Both subsume each other (A⊆B and B⊆A), but we skip already-subsumed
        // Result: one essential, one subsumed
        assertEquals(1, essential.size)
        assertEquals(1, subsumed.size)
    }

    // predictSubsumed tests

    @Test
    fun `predictSubsumed returns empty when no historical data`() {
        val mutations =
            listOf(
                createMutation("A", "method1", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 }),
            )

        val subsumed = analyzer.predictSubsumed(mutations, emptyMap())
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `predictSubsumed returns empty for single mutation`() {
        val mutations =
            listOf(
                createMutation("A", "method1", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 }),
            )
        val historical = mapOf(mutations[0].id to setOf("Test1"))

        val subsumed = analyzer.predictSubsumed(mutations, historical)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `predictSubsumed identifies subsumed mutations from history`() {
        val mutations =
            listOf(
                createMutation("A", "method1", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 }),
                createMutation("A", "method1", 20, ByteArray(10) { 0x03 }, ByteArray(10) { 0x04 }),
            )
        // M1 killed by {T1, T2}, M2 killed by {T1, T2, T3} → M2 subsumes M1
        val historical =
            mapOf(
                mutations[0].id to setOf("Test1", "Test2"),
                mutations[1].id to setOf("Test1", "Test2", "Test3"),
            )

        val subsumed = analyzer.predictSubsumed(mutations, historical)
        assertEquals(1, subsumed.size)
        assertTrue(subsumed.contains(mutations[0].id))
    }

    @Test
    fun `predictSubsumed only considers same class and method`() {
        val mutations =
            listOf(
                createMutation("A", "method1", 10, ByteArray(10) { 0x01 }, ByteArray(10) { 0x02 }),
                createMutation("A", "method2", 20, ByteArray(10) { 0x03 }, ByteArray(10) { 0x04 }),
            )
        val historical =
            mapOf(
                mutations[0].id to setOf("Test1", "Test2"),
                mutations[1].id to setOf("Test1", "Test2", "Test3"),
            )

        val subsumed = analyzer.predictSubsumed(mutations, historical)
        // Different methods → no subsumption
        assertTrue(subsumed.isEmpty())
    }

    private fun createMutation(
        className: String,
        methodName: String,
        lineNumber: Int,
        originalBytecode: ByteArray,
        mutatedBytecode: ByteArray,
    ): Mutation {
        return Mutation(
            id = "ARITHMETIC::$className::$methodName::$lineNumber",
            className = className,
            methodName = methodName,
            methodDescriptor = "()V",
            operator = MutationOperator.ARITHMETIC,
            lineNumber = lineNumber,
            originalBytecode = originalBytecode,
            mutatedBytecode = mutatedBytecode,
            description = "Test mutation",
        )
    }
}
