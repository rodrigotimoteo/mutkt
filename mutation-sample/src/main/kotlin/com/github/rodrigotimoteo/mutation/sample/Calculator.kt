package com.github.rodrigotimoteo.mutation.sample

/**
 * Sample calculator class to demonstrate mutation testing.
 */
class Calculator {

    /**
     * Adds two numbers.
     */
    fun add(a: Int, b: Int): Int = a + b

    /**
     * Subtracts two numbers.
     */
    fun subtract(a: Int, b: Int): Int = a - b

    /**
     * Multiplies two numbers.
     */
    fun multiply(a: Int, b: Int): Int = a * b

    /**
     * Divides two numbers.
     */
    fun divide(a: Int, b: Int): Int {
        if (b == 0) throw IllegalArgumentException("Cannot divide by zero")
        return a / b
    }

    /**
     * Checks if number is positive.
     */
    fun isPositive(n: Int): Boolean = n > 0

    /**
     * Checks if number is in range.
     */
    fun isInRange(value: Int, min: Int, max: Int): Boolean {
        return value >= min && value <= max
    }

    /**
     * Returns the larger of two numbers.
     */
    fun max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    /**
     * Inverts a boolean value.
     */
    fun invert(flag: Boolean): Boolean = !flag
}