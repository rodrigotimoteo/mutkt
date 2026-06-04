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
        assertEquals(1, calc.add(0, 1))
        assertEquals(-1, calc.add(0, -1))
    }

    @Test
    fun `subtract should return difference`() {
        assertEquals(1, calc.subtract(3, 2))
        assertEquals(5, calc.subtract(2, -3))
        assertEquals(-5, calc.subtract(-2, 3))
        assertEquals(0, calc.subtract(5, 5))
        assertEquals(-1, calc.subtract(0, 1))
    }

    @Test
    fun `multiply should return product`() {
        assertEquals(6, calc.multiply(2, 3))
        assertEquals(0, calc.multiply(5, 0))
        assertEquals(0, calc.multiply(0, 5))
        assertEquals(-10, calc.multiply(-2, 5))
        assertEquals(10, calc.multiply(-2, -5))
    }

    @Test
    fun `divide should return quotient`() {
        assertEquals(2, calc.divide(6, 3))
        assertEquals(-2, calc.divide(-6, 3))
        assertEquals(2, calc.divide(-6, -3))
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
        assertTrue(calc.isPositive(1))
        assertFalse(calc.isPositive(-3))
        assertFalse(calc.isPositive(-1))
        assertFalse(calc.isPositive(0))
    }

    @Test
    fun `isInRange should check value is within range`() {
        assertTrue(calc.isInRange(5, 0, 10))
        assertTrue(calc.isInRange(0, 0, 10))
        assertTrue(calc.isInRange(10, 0, 10))
        assertFalse(calc.isInRange(-1, 0, 10))
        assertFalse(calc.isInRange(11, 0, 10))
        assertTrue(calc.isInRange(5, 5, 5))
    }

    @Test
    fun `max should return larger number`() {
        assertEquals(5, calc.max(5, 3))
        assertEquals(5, calc.max(3, 5))
        assertEquals(3, calc.max(3, 3))
        assertEquals(0, calc.max(0, -1))
        assertEquals(-1, calc.max(-1, -2))
    }

    @Test
    fun `invert should flip boolean value`() {
        assertFalse(calc.invert(true))
        assertTrue(calc.invert(false))
    }

    @Test
    fun `absolute should return absolute value`() {
        assertEquals(5, calc.absolute(5))
        assertEquals(5, calc.absolute(-5))
        assertEquals(0, calc.absolute(0))
    }

    // === Kotlin-specific tests ===

    @Test
    fun `processString should handle null safely`() {
        assertEquals("DEFAULT", calc.processString(null))
        assertEquals("HELLO", calc.processString("  hello  "))
        assertEquals("", calc.processString(""))
        assertEquals("WORLD", calc.processString("world"))
    }

    @Test
    fun `requireNonNull should throw on null`() {
        assertThrows<NullPointerException> {
            calc.requireNonNull(null)
        }
        assertEquals("hello", calc.requireNonNull("  hello  "))
        assertEquals("WORLD", calc.requireNonNull("WORLD"))
    }

    @Test
    fun `provideDefault should use default on null`() {
        assertEquals("default", calc.provideDefault(null))
        assertEquals("hello", calc.provideDefault("hello"))
        assertEquals("WORLD", calc.provideDefault("WORLD"))
    }

    // === Increment tests ===

    @Test
    fun `countUp should count from 0 to n`() {
        assertEquals(0, calc.countUp(0))
        assertEquals(3, calc.countUp(3))
        assertEquals(5, calc.countUp(5))
    }

    @Test
    fun `countDown should count from n to 0`() {
        assertEquals(0, calc.countDown(0))
        assertEquals(0, calc.countDown(3))
        assertEquals(0, calc.countDown(5))
    }

    // === Boolean return tests ===

    @Test
    fun `isEven should check if number is even`() {
        assertTrue(calc.isEven(0))
        assertTrue(calc.isEven(2))
        assertTrue(calc.isEven(-2))
        assertFalse(calc.isEven(1))
        assertFalse(calc.isEven(-1))
        assertFalse(calc.isEven(3))
    }

    // === Void method call tests ===

    @Test
    fun `performAdd should update lastOperation`() {
        assertEquals(5, calc.performAdd(2, 3))
        assertEquals("add", calc.lastOperation)
        assertEquals(-1, calc.performAdd(-2, 1))
        assertEquals("add", calc.lastOperation)
    }

    // === Constructor call tests ===

    @Test
    fun `createUser should create user with name`() {
        val user = calc.createUser("Alice")
        assertEquals("Alice", user.name)
        assertEquals(0, user.age)
        assertEquals("", user.email)
    }

    // === Non-void method call tests ===

    @Test
    fun `processWithHelper should add then multiply`() {
        assertEquals(10, calc.processWithHelper(2, 3)) // (2+3)*2 = 10
        assertEquals(6, calc.processWithHelper(1, 2)) // (1+2)*2 = 6
        assertEquals(0, calc.processWithHelper(-1, 1)) // (-1+1)*2 = 0
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
        assertEquals("hello", service.handleResult(Result.Success("hello")))
        assertEquals("", service.handleResult(Result.Success("")))
    }

    @Test
    fun `handleResult should return error message for Error`() {
        assertEquals("Error: fail", service.handleResult(Result.Error("fail")))
        assertEquals("Error: error", service.handleResult(Result.Error("error")))
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

    @Test
    fun `processUser with copy should work`() {
        val user = User("Charlie", 10, "charlie@test.com")
        val updated = user.copy(age = user.age + 1)
        assertEquals(11, updated.age)
        assertEquals("Charlie", updated.name)
        assertEquals("charlie@test.com", updated.email)
    }
}

/**
 * Tests for PartialSuppression - demonstrates @SuppressMutations on specific methods.
 */
class PartialSuppressionTest {
    private val suppression = PartialSuppression()

    @Test
    fun `getValue should return 42`() {
        assertEquals(42, suppression.getValue())
    }

    @Test
    fun `calculate should return sum`() {
        assertEquals(5, suppression.calculate(2, 3))
        assertEquals(0, suppression.calculate(-1, 1))
        assertEquals(-3, suppression.calculate(-1, -2))
    }
}
