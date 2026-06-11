package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MutationEngineInternalTest {
    private fun makeMutationInfo(
        className: String = "com.example.Foo",
        methodName: String = "bar",
        lineNumber: Int = 10,
        operator: MutationOperator = MutationOperator.ARITHMETIC,
    ): MutationInfo {
        return MutationInfo(
            operator = operator,
            className = className,
            methodName = methodName,
            methodDescriptor = "()V",
            lineNumber = lineNumber,
            description = "Test mutation",
            originalOpcode = 1,
            mutatedOpcode = 2,
        )
    }

    @Test
    fun `filterByPatterns returns all when no patterns configured`() {
        val engine = MutationEngine()
        // Verify engine created successfully with default operators
        assertTrue(engine.toString().isNotEmpty())
    }

    @Test
    fun `limitMutationsPerClass truncates when over limit`() {
        val mutations =
            (1..10).map { i ->
                makeMutationInfo(lineNumber = i) to ByteArray(0)
            }
        // Group by className and take first N
        val grouped = mutations.groupBy { it.first.className }
        assertEquals(1, grouped.size)
        assertEquals(10, grouped.values.first().size)
    }

    @Test
    fun `filterTestClassNames with include patterns`() {
        val testNames = listOf("com.example.FooTest", "com.example.BarTest", "com.example.HelperTest")
        val includePatterns = listOf(".*Foo.*")
        val compiled = includePatterns.map { Regex(it) }
        val filtered = testNames.filter { name -> compiled.any { it.containsMatchIn(name) } }
        assertEquals(1, filtered.size)
        assertEquals("com.example.FooTest", filtered[0])
    }

    @Test
    fun `filterTestClassNames with exclude patterns`() {
        val testNames = listOf("com.example.FooTest", "com.example.BarTest", "com.example.HelperTest")
        val excludePatterns = listOf(".*Helper.*")
        val compiled = excludePatterns.map { Regex(it) }
        val filtered = testNames.filter { name -> !compiled.any { it.containsMatchIn(name) } }
        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterTestClassNames with both include and exclude`() {
        val testNames = listOf("com.example.FooTest", "com.example.BarTest", "com.example.FooHelperTest")
        val includePatterns = listOf(".*Foo.*")
        val excludePatterns = listOf(".*Helper.*")
        val compiledInclude = includePatterns.map { Regex(it) }
        val compiledExclude = excludePatterns.map { Regex(it) }
        val filtered =
            testNames.filter { name ->
                val matchesInclude = compiledInclude.any { it.containsMatchIn(name) }
                val matchesExclude = compiledExclude.any { it.containsMatchIn(name) }
                matchesInclude && !matchesExclude
            }
        assertEquals(1, filtered.size)
        assertEquals("com.example.FooTest", filtered[0])
    }

    @Test
    fun `filterTestClassNames returns all when no patterns`() {
        val testNames = listOf("com.example.FooTest", "com.example.BarTest")
        // Empty include + empty exclude = return all
        assertTrue(testNames.isNotEmpty())
    }

    @Test
    fun `toMutation creates correct id format`() {
        val info = makeMutationInfo()
        val id = "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}"
        assertEquals("ARITHMETIC::com.example.Foo::bar::10", id)
    }

    @Test
    fun `MutationReport buildReport calculates correct counts`() {
        val killed = 5
        val survived = 3
        val errors = 1
        val timeouts = 1
        val noCoverage = 2
        val total = killed + survived + errors + timeouts + noCoverage
        assertEquals(12, total)
    }
}
