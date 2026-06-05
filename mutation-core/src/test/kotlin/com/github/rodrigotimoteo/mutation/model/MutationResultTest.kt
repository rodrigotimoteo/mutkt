package com.github.rodrigotimoteo.mutation.model

import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutationResultTest {
    @Test
    fun `MutationResult with KILLED status isKilled is true`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.KILLED, 100L)
        assertTrue(result.isKilled)
    }

    @Test
    fun `MutationResult with SURVIVED status isSurvived is true`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.SURVIVED, 100L)
        assertTrue(result.isSurvived)
    }

    @Test
    fun `MutationResult KILLED isSurvived is false`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.KILLED, 100L)
        assertFalse(result.isSurvived)
    }

    @Test
    fun `MutationResult SURVIVED isKilled is false`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.SURVIVED, 100L)
        assertFalse(result.isKilled)
    }

    @Test
    fun `MutationResult ERROR is neither killed nor survived`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.ERROR, 100L, "boom")
        assertFalse(result.isKilled)
        assertFalse(result.isSurvived)
    }

    @Test
    fun `MutationResult TIMEOUT is neither killed nor survived`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.TIMEOUT, 100L)
        assertFalse(result.isKilled)
        assertFalse(result.isSurvived)
    }

    @Test
    fun `MutationResult default executionTimeMs is 0`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.KILLED)
        assertEquals(0L, result.executionTimeMs)
    }

    @Test
    fun `MutationResult default errorMessage is null`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.KILLED)
        assertNull(result.errorMessage)
    }

    @Test
    fun `MutationResult preserves error message`() {
        val mutation = createTestMutation()
        val result = MutationResult(mutation, MutationStatus.ERROR, 50L, "NullPointerException")
        assertEquals("NullPointerException", result.errorMessage)
    }
}

class ClassMutationScoreTest {
    @Test
    fun `score is killedMutations times 100 divided by totalMutations`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 10, killedMutations = 7, survivedMutations = 3)
        assertEquals(70, score.score)
    }

    @Test
    fun `score is 0 when totalMutations is 0`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 0, killedMutations = 0, survivedMutations = 0)
        assertEquals(0, score.score)
    }

    @Test
    fun `score is 100 when all mutations are killed`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 5, killedMutations = 5, survivedMutations = 0)
        assertEquals(100, score.score)
    }

    @Test
    fun `score is 0 when no mutations are killed`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 5, killedMutations = 0, survivedMutations = 5)
        assertEquals(0, score.score)
    }

    @Test
    fun `score integer division (no rounding)`() {
        // 7/10 = 70% but 7/11 = 63% (integer division)
        val score = ClassMutationScore("com.Foo", totalMutations = 11, killedMutations = 7, survivedMutations = 4)
        assertEquals(63, score.score)
    }

    @Test
    fun `default weakMutations is 0`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 10, killedMutations = 5, survivedMutations = 5)
        assertEquals(0, score.weakMutations)
    }

    @Test
    fun `default subsumedMutations is 0`() {
        val score = ClassMutationScore("com.Foo", totalMutations = 10, killedMutations = 5, survivedMutations = 5)
        assertEquals(0, score.subsumedMutations)
    }

    @Test
    fun `className is preserved`() {
        val score = ClassMutationScore("com.example.Foo", 1, 0, 1)
        assertEquals("com.example.Foo", score.className)
    }
}

class MutationReportTest {
    @Test
    fun `killedPercentage is 0 when totalMutations is 0`() {
        val report = MutationReport(emptyList(), 0, 0, 0, 0, 0, 0, 0L)
        assertEquals(0, report.killedPercentage)
    }

    @Test
    fun `killedPercentage is killedMutations times 100 divided by totalMutations`() {
        val report =
            MutationReport(
                emptyList(),
                totalMutations = 10,
                killedMutations = 7,
                survivedMutations = 3,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 100L,
            )
        assertEquals(70, report.killedPercentage)
    }

    @Test
    fun `survivedPercentage is survivedMutations times 100 divided by totalMutations`() {
        val report =
            MutationReport(
                emptyList(),
                totalMutations = 10,
                killedMutations = 5,
                survivedMutations = 3,
                errorMutations = 1,
                timeoutMutations = 1,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 100L,
            )
        assertEquals(30, report.survivedPercentage)
    }

    @Test
    fun `survivedPercentage is 0 when totalMutations is 0`() {
        val report = MutationReport(emptyList(), 0, 0, 0, 0, 0, 0, 0L)
        assertEquals(0, report.survivedPercentage)
    }

    @Test
    fun `killedPercentage is 100 when all killed`() {
        val report =
            MutationReport(
                emptyList(),
                totalMutations = 5,
                killedMutations = 5,
                survivedMutations = 0,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 50L,
            )
        assertEquals(100, report.killedPercentage)
    }

    @Test
    fun `getClassScores groups by className`() {
        val m1 = createTestMutation(className = "com.Foo")
        val m2 = createTestMutation(className = "com.Foo")
        val m3 = createTestMutation(className = "com.Bar")
        val results =
            listOf(
                MutationResult(m1, MutationStatus.KILLED, 100L),
                MutationResult(m2, MutationStatus.SURVIVED, 50L),
                MutationResult(m3, MutationStatus.KILLED, 30L),
            )
        val report = MutationReport(results, 3, 2, 1, 0, 0, 0, 180L)
        val scores = report.getClassScores()
        assertEquals(2, scores.size)
    }

    @Test
    fun `getClassScores sorts by score descending`() {
        val m1 = createTestMutation(className = "com.Foo")
        val m2 = createTestMutation(className = "com.Bar")
        val results =
            listOf(
                // com.Foo: 0% killed
                MutationResult(m1, MutationStatus.SURVIVED, 100L),
                // com.Bar: 100% killed
                MutationResult(m2, MutationStatus.KILLED, 30L),
            )
        val report = MutationReport(results, 2, 1, 1, 0, 0, 0, 130L)
        val scores = report.getClassScores()
        // Bar first (100%), then Foo (0%)
        assertEquals("com.Bar", scores[0].className)
        assertEquals("com.Foo", scores[1].className)
    }

    @Test
    fun `getClassScores counts killed and survived per class`() {
        val m1 = createTestMutation(className = "com.Foo")
        val m2 = createTestMutation(className = "com.Foo")
        val m3 = createTestMutation(className = "com.Foo")
        val results =
            listOf(
                MutationResult(m1, MutationStatus.KILLED, 0L),
                MutationResult(m2, MutationStatus.KILLED, 0L),
                MutationResult(m3, MutationStatus.SURVIVED, 0L),
            )
        val report = MutationReport(results, 3, 2, 1, 0, 0, 0, 0L)
        val scores = report.getClassScores()
        val fooScore = scores.first { it.className == "com.Foo" }
        assertEquals(3, fooScore.totalMutations)
        assertEquals(2, fooScore.killedMutations)
        assertEquals(1, fooScore.survivedMutations)
    }

    @Test
    fun `getClassScores on empty report returns empty list`() {
        val report = MutationReport(emptyList(), 0, 0, 0, 0, 0, 0, 0L)
        assertEquals(0, report.getClassScores().size)
    }

    @Test
    fun `totalExecutionTimeMs is preserved`() {
        val report = MutationReport(emptyList(), 0, 0, 0, 0, 0, 0, 12345L)
        assertEquals(12345L, report.totalExecutionTimeMs)
    }
}

private fun createTestMutation(className: String = "com.example.Foo"): Mutation {
    return Mutation(
        id = "m1",
        className = className,
        methodName = "test",
        methodDescriptor = "()I",
        operator = MutationOperator.ARITHMETIC,
        lineNumber = 10,
        originalBytecode = byteArrayOf(),
        mutatedBytecode = byteArrayOf(),
        description = "test",
    )
}
