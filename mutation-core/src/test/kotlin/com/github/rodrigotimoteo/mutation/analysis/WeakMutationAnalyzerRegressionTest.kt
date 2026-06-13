package com.github.rodrigotimoteo.mutation.analysis

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WeakMutationAnalyzerRegressionTest {
    private lateinit var analyzer: WeakMutationAnalyzer

    @BeforeEach
    fun setUp() {
        analyzer = WeakMutationAnalyzer()
    }

    @Test
    fun `isClassReachable normalizes slashed inner class to dotted`() {
        val className = "com/example/Outer\$Inner"
        val coveredClasses = setOf("com.example.Outer\$Inner")

        val result = analyzer.isClassReachable(className, coveredClasses)

        assertTrue(result)
    }

    @Test
    fun `filterUnreachableClasses maps slashed inner class name`() {
        val mutation = createMutation(className = "com/example/Outer\$Inner")
        val mutations = listOf(mutation)
        val coveredClasses = setOf("com.example.Outer\$Inner")

        val result = analyzer.filterUnreachableClasses(mutations, coveredClasses)

        assertTrue(result.contains(mutation))
    }

    @Test
    fun `filterUnreachable drops mutation when covered lines set is empty`() {
        val mutation = createMutation(className = "com.example.Foo", lineNumber = 10)
        val mutations = listOf(mutation)
        val coveredLinesMap = mapOf("com.example.Foo" to emptySet<Int>())

        val result = analyzer.filterUnreachable(mutations, coveredLinesMap)

        assertFalse(result.contains(mutation))
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
            description = "Regression mutation",
            originalOpcode = 0,
            mutatedOpcode = 0,
        )
    }
}
