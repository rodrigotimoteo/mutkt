package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WeakMutationAnalyzerFullTest {
    private lateinit var analyzer: WeakMutationAnalyzer

    @BeforeEach
    fun setUp() {
        analyzer = WeakMutationAnalyzer()
    }

    // isReachable tests

    @Test
    fun `isReachable returns true when mutation line is in covered lines`() {
        val mutation = createMutation(lineNumber = 10)
        val coveredLines = setOf(5, 10, 15)

        val result = analyzer.isReachable(mutation, coveredLines)

        assertTrue(result)
    }

    @Test
    fun `isReachable returns false when mutation line is not in covered lines`() {
        val mutation = createMutation(lineNumber = 20)
        val coveredLines = setOf(5, 10, 15)

        val result = analyzer.isReachable(mutation, coveredLines)

        assertFalse(result)
    }

    @Test
    fun `isReachable returns false when covered lines is empty`() {
        val mutation = createMutation(lineNumber = 10)
        val coveredLines = emptySet<Int>()

        val result = analyzer.isReachable(mutation, coveredLines)

        assertFalse(result)
    }

    @Test
    fun `isReachable returns true for negative line numbers when in covered set`() {
        val mutation = createMutation(lineNumber = -1)
        val coveredLines = setOf(-1, 0, 1)

        val result = analyzer.isReachable(mutation, coveredLines)

        assertTrue(result)
    }

    // isClassReachable tests

    @Test
    fun `isClassReachable returns true when class is in covered set`() {
        val className = "com.example.Foo"
        val coveredClasses = setOf("com.example.Foo", "com.example.Bar")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertTrue(result)
    }

    @Test
    fun `isClassReachable returns false when class is not in covered set`() {
        val className = "com.example.Baz"
        val coveredClasses = setOf("com.example.Foo", "com.example.Bar")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertFalse(result)
    }

    @Test
    fun `isClassReachable returns false when covered set is empty`() {
        val className = "com.example.Foo"
        val coveredClasses = emptySet<String>()

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertFalse(result)
    }

    @Test
    fun `isClassReachable returns true for slashed class name when dotted version is in set`() {
        val className = "com/example/Foo"
        val coveredClasses = setOf("com.example.Foo")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertTrue(result)
    }

    @Test
    fun `isClassReachable returns true for dotted class name when slashed version is in set`() {
        val className = "com.example.Foo"
        val coveredClasses = setOf("com/example/Foo")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertTrue(result)
    }

    // filterUnreachable tests

    @Test
    fun `filterUnreachable removes mutations not in covered lines`() {
        val mutation1 = createMutation(className = "Foo", lineNumber = 10)
        val mutation2 = createMutation(className = "Foo", lineNumber = 20)
        val mutation3 = createMutation(className = "Foo", lineNumber = 30)
        val mutations = listOf(mutation1, mutation2, mutation3)
        val coveredLinesMap = mapOf("Foo" to setOf(10, 30))

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation3))
        assertFalse(result.contains(mutation2))
    }

    @Test
    fun `filterUnreachable keeps all mutations when covered lines map is empty`() {
        val mutation1 = createMutation(lineNumber = 10)
        val mutation2 = createMutation(lineNumber = 20)
        val mutations = listOf(mutation1, mutation2)
        val coveredLinesMap = emptyMap<String, Set<Int>>()

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation2))
    }

    @Test
    fun `filterUnreachable handles slashed class names in map`() {
        val mutation = createMutation(className = "com.example.Foo", lineNumber = 10)
        val mutations = listOf(mutation)
        val coveredLinesMap = mapOf("com/example/Foo" to setOf(10))

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertEquals(1, result.size)
        assertTrue(result.contains(mutation))
    }

    @Test
    fun `filterUnreachable removes all mutations when class has no covered lines`() {
        val mutation = createMutation(className = "Foo", lineNumber = 10)
        val mutations = listOf(mutation)
        val coveredLinesMap = mapOf("Bar" to setOf(10))

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterUnreachable handles empty mutation list`() {
        val coveredLinesMap = mapOf("Foo" to setOf(10))

        val result = analyzer.filterUnreachable(emptyList(), coveredLinesMap)

        assertTrue(result.isEmpty())
    }

    // filterUnreachableClasses tests

    @Test
    fun `filterUnreachableClasses removes mutations in uncovered classes`() {
        val mutation1 = createMutation(className = "com.example.Foo")
        val mutation2 = createMutation(className = "com.example.Bar")
        val mutation3 = createMutation(className = "com.example.Baz")
        val mutations = listOf(mutation1, mutation2, mutation3)
        val coveredClasses = setOf("com.example.Foo", "com.example.Baz")

        val result = analyzer.filterUnreachableClasses(mutations, coveredClasses)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation3))
        assertFalse(result.contains(mutation2))
    }

    @Test
    fun `filterUnreachableClasses keeps all mutations when covered classes is empty`() {
        val mutation1 = createMutation(className = "Foo")
        val mutation2 = createMutation(className = "Bar")
        val mutations = listOf(mutation1, mutation2)
        val coveredClasses = emptySet<String>()

        val result = analyzer.filterUnreachableClasses(mutations, coveredClasses)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation2))
    }

    @Test
    fun `filterUnreachableClasses handles slashed class names in covered set`() {
        val mutation = createMutation(className = "com.example.Foo")
        val mutations = listOf(mutation)
        val coveredClasses = setOf("com/example/Foo")

        val result = analyzer.filterUnreachableClasses(mutations, coveredClasses)

        assertEquals(1, result.size)
        assertTrue(result.contains(mutation))
    }

    @Test
    fun `filterUnreachableClasses handles empty mutation list`() {
        val coveredClasses = setOf("Foo")

        val result = analyzer.filterUnreachableClasses(emptyList(), coveredClasses)

        assertTrue(result.isEmpty())
    }

    // Combined filter tests

    @Test
    fun `filter applies both class and line level filtering`() {
        val mutation1 = createMutation(className = "Foo", lineNumber = 10)
        val mutation2 = createMutation(className = "Foo", lineNumber = 20)
        val mutation3 = createMutation(className = "Bar", lineNumber = 10)
        val mutations = listOf(mutation1, mutation2, mutation3)
        val coveredLinesMap = mapOf("Foo" to setOf(10), "Bar" to setOf(10))
        val coveredClasses = setOf("Foo")

        val result = analyzer.filter(mutations, coveredLinesMap, coveredClasses)

        assertEquals(1, result.size)
        assertTrue(result.contains(mutation1))
    }

    @Test
    fun `filter keeps mutations when both coverage inputs are empty`() {
        val mutation1 = createMutation(className = "Foo", lineNumber = 10)
        val mutation2 = createMutation(className = "Bar", lineNumber = 20)
        val mutations = listOf(mutation1, mutation2)

        val result = analyzer.filter(mutations, emptyMap(), emptySet())

        assertEquals(2, result.size)
    }

    @Test
    fun `filter removes all when classes are covered but lines are not`() {
        val mutation = createMutation(className = "Foo", lineNumber = 10)
        val mutations = listOf(mutation)
        val coveredLinesMap = mapOf("Foo" to setOf(20))
        val coveredClasses = setOf("Foo")

        val result = analyzer.filter(mutations, coveredLinesMap, coveredClasses)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter removes all when lines are covered but classes are not`() {
        val mutation = createMutation(className = "Foo", lineNumber = 10)
        val mutations = listOf(mutation)
        val coveredLinesMap = mapOf("Foo" to setOf(10))
        val coveredClasses = setOf("Bar")

        val result = analyzer.filter(mutations, coveredLinesMap, coveredClasses)

        assertTrue(result.isEmpty())
    }

    // Edge cases

    @Test
    fun `filterUnreachable handles multiple classes in map`() {
        val mutation1 = createMutation(className = "Foo", lineNumber = 10)
        val mutation2 = createMutation(className = "Bar", lineNumber = 5)
        val mutation3 = createMutation(className = "Baz", lineNumber = 1)
        val mutations = listOf(mutation1, mutation2, mutation3)
        val coveredLinesMap =
            mapOf(
                "Foo" to setOf(10),
                "Bar" to setOf(5),
            )

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation2))
        assertFalse(result.contains(mutation3))
    }

    @Test
    fun `filterUnreachableClasses handles multiple classes`() {
        val mutation1 = createMutation(className = "Foo")
        val mutation2 = createMutation(className = "Bar")
        val mutation3 = createMutation(className = "Baz")
        val mutations = listOf(mutation1, mutation2, mutation3)
        val coveredClasses = setOf("Foo", "Bar")

        val result = analyzer.filterUnreachableClasses(mutations, coveredClasses)

        assertEquals(2, result.size)
        assertTrue(result.contains(mutation1))
        assertTrue(result.contains(mutation2))
        assertFalse(result.contains(mutation3))
    }

    @Test
    fun `isReachable handles line number zero`() {
        val mutation = createMutation(lineNumber = 0)
        val coveredLines = setOf(0, 1, 2)

        val result = analyzer.isReachable(mutation, coveredLines)

        assertTrue(result)
    }

    @Test
    fun `isClassReachable handles inner class names`() {
        val className = "com.example.Foo\$Inner"
        val coveredClasses = setOf("com.example.Foo\$Inner")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertTrue(result)
    }

    private fun createMutation(
        className: String = "com.example.Test",
        methodName: String = "testMethod",
        lineNumber: Int = 1,
    ): MutationInfo {
        return MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = className,
            methodName = methodName,
            methodDescriptor = "()V",
            lineNumber = lineNumber,
            description = "Test mutation",
            originalOpcode = 0,
            mutatedOpcode = 0,
        )
    }
}
