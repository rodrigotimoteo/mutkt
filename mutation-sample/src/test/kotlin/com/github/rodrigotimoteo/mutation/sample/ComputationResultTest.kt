package com.github.rodrigotimoteo.mutation.sample

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ComputationResultTest {

    @Nested
    inner class SuccessTests {
        @Test
        fun `success result has value`() {
            val result = ComputationResult.Success(42.0)
            assertEquals(42.0, result.value)
        }

        @Test
        fun `processResult with success`() {
            val result = ComputationResult.Success(42.0)
            assertEquals("Value: 42.0", processResult(result))
        }

        @Test
        fun `resultValue extracts success value`() {
            val result = ComputationResult.Success(42.0)
            assertEquals(42.0, resultValue(result))
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `error result has message`() {
            val result = ComputationResult.Error("boom")
            assertEquals("boom", result.message)
        }

        @Test
        fun `processResult with error`() {
            val result = ComputationResult.Error("boom")
            assertEquals("Error: boom", processResult(result))
        }

        @Test
        fun `resultValue returns null for error`() {
            val result = ComputationResult.Error("boom")
            assertNull(resultValue(result))
        }
    }

    @Nested
    inner class LoadingTests {
        @Test
        fun `processResult with loading`() {
            val result = ComputationResult.Loading
            assertEquals("Loading...", processResult(result))
        }

        @Test
        fun `resultValue returns null for loading`() {
            val result = ComputationResult.Loading
            assertNull(resultValue(result))
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [1.0, 2.5, 100.0, -5.0])
    fun `success resultValue returns correct value for various doubles`(value: Double) {
        val result = ComputationResult.Success(value)
        assertEquals(value, resultValue(result))
    }
}
