package com.github.rodrigotimoteo.mutation.sample

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sample tests for Calculator - used to validate mutation testing.
 * These tests should KILL most mutants.
 */
class CalculatorTest {

    private val calc = Calculator()

    @Test
    fun `add should return sum of two numbers`() {
        assertEquals(5, calc.add(2, 3))
        assertEquals(0, calc.add(-1, 1))
        assertEquals(-3, calc.add(-1, -2))
    }

    @Test
    fun `subtract should return difference`() {
        assertEquals(1, calc.subtract(3, 2))
        assertEquals(5, calc.subtract(2, -3))
        assertEquals(-5, calc.subtract(-2, 3))
    }

    @Test
    fun `multiply should return product`() {
        assertEquals(6, calc.multiply(2, 3))
        assertEquals(0, calc.multiply(5, 0))
        assertEquals(-10, calc.multiply(-2, 5))
    }

    @Test
    fun `divide should return quotient`() {
        assertEquals(2, calc.divide(6, 3))
        assertEquals(-2, calc.divide(-6, 3))
        assertEquals(0, calc.divide(0, 5))
    }

    @Test
    fun `divide by zero should throw exception`() {
        assertThrows<IllegalArgumentException> {
            calc.divide(10, 0)
        }
    }

    @Test
    fun `isPositive should return true for positive numbers`() {
        assertTrue(calc.isPositive(5))
        assertFalse(calc.isPositive(-3))
        assertFalse(calc.isPositive(0))
    }

    @Test
    fun `isInRange should check value is within range`() {
        assertTrue(calc.isInRange(5, 0, 10))
        assertTrue(calc.isInRange(0, 0, 10))
        assertTrue(calc.isInRange(10, 0, 10))
        assertFalse(calc.isInRange(-1, 0, 10))
        assertFalse(calc.isInRange(11, 0, 10))
    }

    @Test
    fun `max should return larger number`() {
        assertEquals(5, calc.max(5, 3))
        assertEquals(5, calc.max(3, 5))
        assertEquals(3, calc.max(3, 3))
        assertEquals(0, calc.max(0, -1))
    }

    @Test
    fun `invert should flip boolean value`() {
        assertFalse(calc.invert(true))
        assertTrue(calc.invert(false))
    }

    // Kotlin-specific tests

    @Test
    fun `processString should handle null safely`() {
        assertEquals("DEFAULT", calc.processString(null))
        assertEquals("HELLO", calc.processString("  hello  "))
        assertEquals("", calc.processString(""))
    }

    @Test
    fun `requireNonNull should throw on null`() {
        assertThrows<NullPointerException> {
            calc.requireNonNull(null)
        }
        assertEquals("hello", calc.requireNonNull("  hello  "))
    }

    @Test
    fun `provideDefault should use default on null`() {
        assertEquals("default", calc.provideDefault(null))
        assertEquals("hello", calc.provideDefault("hello"))
    }
}

/**
 * Tests for UserService - sealed class and data class mutations.
 */
class UserServiceTest {
    private val service = UserService()

    @Test
    fun `handleResult should return data for Success`() {
        assertEquals("test", service.handleResult(Result.Success("test")))
    }

    @Test
    fun `handleResult should return error message for Error`() {
        assertEquals("Error: fail", service.handleResult(Result.Error("fail")))
    }

    @Test
    fun `handleResult should return Loading for Loading`() {
        assertEquals("Loading...", service.handleResult(Result.Loading))
    }

    @Test
    fun `processUser should increment age`() {
        val user = User("Alice", 30, "alice@test.com")
        val updated = service.processUser(user)
        assertEquals(31, updated.age)
        assertEquals("Alice", updated.name)
        assertEquals("alice@test.com", updated.email)
    }

    @Test
    fun `processUser should preserve other fields`() {
        val user = User("Bob", 25, "bob@test.com")
        val updated = service.processUser(user)
        assertEquals(user.name, updated.name)
        assertEquals(user.email, updated.email)
    }
}