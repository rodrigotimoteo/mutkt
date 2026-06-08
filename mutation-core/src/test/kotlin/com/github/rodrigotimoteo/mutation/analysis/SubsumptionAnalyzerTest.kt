package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationStatus
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
        val results =
            mapOf(
                mutations[0].id to MutationStatus.KILLED,
                mutations[1].id to MutationStatus.SURVIVED,
            )

        val (essential, subsumed) = analyzer.analyze(mutations, results)

        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze returns all mutations as essential (conservative mode)`() {
        val original = ByteArray(10) { 0x01 }
        val mutatedA = ByteArray(10) { 0x01 }.also { it[0] = 0x02 }
        val mutatedB = ByteArray(10) { 0x01 }.also { it[0] = 0x03 }

        val mutationA = createMutation("Foo", "bar", 10, original, mutatedA)
        val mutationB = createMutation("Foo", "bar", 20, original, mutatedB)

        val mutations = listOf(mutationA, mutationB)
        val results =
            mapOf(
                mutationA.id to MutationStatus.KILLED,
                mutationB.id to MutationStatus.SURVIVED,
            )

        val (essential, subsumed) = analyzer.analyze(mutations, results)

        // Conservative: no subsumption detected
        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze does not subsume across different classes`() {
        val original = ByteArray(10) { 0x01 }
        val mutatedA = ByteArray(10) { 0x01 }.also { it[0] = 0x02 }
        val mutatedB = ByteArray(10) { 0x01 }.also { it[0] = 0x03 }

        val mutationA = createMutation("Foo", "bar", 10, original, mutatedA)
        val mutationB = createMutation("Baz", "bar", 20, original, mutatedB)

        val mutations = listOf(mutationA, mutationB)
        val results =
            mapOf(
                mutationA.id to MutationStatus.KILLED,
                mutationB.id to MutationStatus.SURVIVED,
            )

        val (essential, subsumed) = analyzer.analyze(mutations, results)

        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze does not subsume across different methods`() {
        val original = ByteArray(10) { 0x01 }
        val mutatedA = ByteArray(10) { 0x01 }.also { it[0] = 0x02 }
        val mutatedB = ByteArray(10) { 0x01 }.also { it[0] = 0x03 }

        val mutationA = createMutation("Foo", "bar", 10, original, mutatedA)
        val mutationB = createMutation("Foo", "baz", 20, original, mutatedB)

        val mutations = listOf(mutationA, mutationB)
        val results =
            mapOf(
                mutationA.id to MutationStatus.KILLED,
                mutationB.id to MutationStatus.SURVIVED,
            )

        val (essential, subsumed) = analyzer.analyze(mutations, results)

        assertEquals(2, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze handles single mutation`() {
        val mutation = createMutation("Foo", "bar", 10, ByteArray(5), ByteArray(5))
        val mutations = listOf(mutation)
        val results = mapOf(mutation.id to MutationStatus.KILLED)

        val (essential, subsumed) = analyzer.analyze(mutations, results)

        assertEquals(1, essential.size)
        assertTrue(subsumed.isEmpty())
    }

    @Test
    fun `analyze handles empty list`() {
        val (essential, subsumed) = analyzer.analyze(emptyList(), emptyMap())

        assertTrue(essential.isEmpty())
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
