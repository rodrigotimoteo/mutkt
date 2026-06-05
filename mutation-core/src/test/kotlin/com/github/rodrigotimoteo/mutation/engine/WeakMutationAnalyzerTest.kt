package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeakMutationAnalyzerTest {
    @TempDir
    lateinit var tempDir: Path

    private fun createMutation(
        id: String,
        className: String,
        lineNumber: Int,
    ): Mutation {
        return Mutation(
            id = id,
            className = className,
            methodName = "testMethod",
            methodDescriptor = "()V",
            operator = MutationOperator.ARITHMETIC,
            lineNumber = lineNumber,
            originalBytecode = ByteArray(0),
            mutatedBytecode = ByteArray(0),
            description = "Arithmetic mutation",
        )
    }

    @Test
    fun `analyze without coverage data treats all mutations as strong`() {
        val analyzer = WeakMutationAnalyzer()
        val mutations =
            listOf(
                createMutation("m1", "com.Foo", 10),
                createMutation("m2", "com.Foo", 20),
            )

        val result = analyzer.analyze(mutations, coverageData = null)

        assertEquals(0, result.weakCount)
        assertEquals(2, result.strongCount)
        assertEquals(0, result.weakMutations.size)
        assertEquals(2, result.strongMutations.size)
    }

    @Test
    fun `analyze with coverage data marks uncovered mutation as weak`() {
        val analyzer = WeakMutationAnalyzer()
        val coverageFile = File(tempDir.toFile(), "coverage.exec")
        coverageFile.writeText(
            """
            CLASS:com.Foo
            LINE:10
            LINE:20
            """.trimIndent(),
        )

        val mutations =
            listOf(
                createMutation("m1", "com.Foo", 10),
                createMutation("m2", "com.Foo", 99),
                // line 99 not covered
            )

        val result = analyzer.analyze(mutations, coverageData = coverageFile)

        assertEquals(1, result.weakCount)
        assertEquals(1, result.strongCount)
        assertEquals("m2", result.weakMutations.first().id)
        assertEquals("m1", result.strongMutations.first().id)
    }

    @Test
    fun `analyze with coverage data marks uncovered class mutation as weak`() {
        val analyzer = WeakMutationAnalyzer()
        val coverageFile = File(tempDir.toFile(), "coverage.exec")
        coverageFile.writeText(
            """
            CLASS:com.Foo
            LINE:10
            """.trimIndent(),
        )

        val mutations =
            listOf(
                createMutation("m1", "com.Bar", 10),
                // com.Bar not in coverage
            )

        val result = analyzer.analyze(mutations, coverageData = coverageFile)

        assertEquals(1, result.weakCount)
        assertEquals(0, result.strongCount)
    }

    @Test
    fun `getStrength returns STRONG when line is covered`() {
        val analyzer = WeakMutationAnalyzer()
        val coveredLines: Map<String, Set<Int>> = mapOf("com.Foo" to setOf(10, 20))
        val mutation = createMutation("m1", "com.Foo", 10)

        val strength = analyzer.getStrength(mutation, coveredLines)
        assertEquals(WeakMutationAnalyzer.MutationStrength.STRONG, strength)
    }

    @Test
    fun `getStrength returns WEAK when line is not covered`() {
        val analyzer = WeakMutationAnalyzer()
        val coveredLines: Map<String, Set<Int>> = mapOf("com.Foo" to setOf(10, 20))
        val mutation = createMutation("m1", "com.Foo", 99)

        val strength = analyzer.getStrength(mutation, coveredLines)
        assertEquals(WeakMutationAnalyzer.MutationStrength.WEAK, strength)
    }

    @Test
    fun `getStrength returns STRONG for empty coverage data`() {
        val analyzer = WeakMutationAnalyzer()
        val coveredLines: Map<String, Set<Int>> = emptyMap()
        val mutation = createMutation("m1", "com.Foo", 10)

        val strength = analyzer.getStrength(mutation, coveredLines)
        assertEquals(WeakMutationAnalyzer.MutationStrength.STRONG, strength)
    }

    @Test
    fun `analyze empty mutations list returns zero counts`() {
        val analyzer = WeakMutationAnalyzer()
        val result = analyzer.analyze(emptyList(), coverageData = null)

        assertEquals(0, result.weakCount)
        assertEquals(0, result.strongCount)
        assertTrue(result.weakMutations.isEmpty())
        assertTrue(result.strongMutations.isEmpty())
    }
}
