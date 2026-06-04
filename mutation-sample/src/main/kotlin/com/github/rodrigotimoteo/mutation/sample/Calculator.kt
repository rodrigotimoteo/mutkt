package com.github.rodrigotimoteo.mutation.sample

import com.github.rodrigotimoteo.mutation.annotation.SuppressMutations

/**
 * Data class for demonstrating copy mutations.
 */
data class User(
    val name: String,
    val age: Int,
    val email: String
)

/**
 * Sealed class for demonstrating when mutations.
 */
sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val message: String) : Result()
    object Loading : Result()
}

/**
 * Functions that use sealed class and data class.
 */
class UserService {
    fun handleResult(result: Result): String = when (result) {
        is Result.Success -> result.data
        is Result.Error -> "Error: ${result.message}"
        is Result.Loading -> "Loading..."
    }

    fun processUser(user: User): User {
        return user.copy(age = user.age + 1)
    }
}

/**
 * Sample calculator class to demonstrate mutation testing.
 */
class Calculator {

    /**
     * Adds two numbers.
     * Demonstrates: ARITHMETIC operator (+ -> -, *, /)
     */
    fun add(a: Int, b: Int): Int = a + b

    /**
     * Subtracts two numbers.
     * Demonstrates: ARITHMETIC operator (- -> +, *)
     */
    fun subtract(a: Int, b: Int): Int = a - b

    /**
     * Multiplies two numbers.
     * Demonstrates: ARITHMETIC operator (* -> +, -)
     */
    fun multiply(a: Int, b: Int): Int = a * b

    /**
     * Divides two numbers.
     * Demonstrates: CONDITIONALS_BOUNDARY, ARITHMETIC
     */
    fun divide(a: Int, b: Int): Int {
        if (b == 0) throw IllegalArgumentException("Cannot divide by zero")
        return a / b
    }

    /**
     * Checks if number is positive.
     * Demonstrates: NEGATE_CONDITIONALS (> -> <=)
     */
    fun isPositive(n: Int): Boolean = n > 0

    /**
     * Checks if number is in range.
     * Demonstrates: CONDITIONALS_BOUNDARY (>= -> >, <= -> <)
     */
    fun isInRange(value: Int, min: Int, max: Int): Boolean {
        return value >= min && value <= max
    }

    /**
     * Returns the larger of two numbers.
     * Demonstrates: RETURN_VALS (return a -> return b)
     */
    fun max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    /**
     * Inverts a boolean value.
     * Demonstrates: NEGATE_CONDITIONALS
     */
    fun invert(flag: Boolean): Boolean = !flag

    /**
     * Returns absolute value.
     * Demonstrates: RETURN_VALS, INVERT_NEGS
     */
    fun absolute(n: Int): Int {
        return if (n < 0) -n else n
    }

    // === Kotlin-specific examples ===

    /**
     * Safe call operator (?.let).
     * Demonstrates: NULL_SAFETY operator
     */
    fun processString(input: String?): String {
        return input?.trim()?.uppercase() ?: "DEFAULT"
    }

    /**
     * Not-null assertion (!!).
     * Demonstrates: NULL_SAFETY operator
     */
    fun requireNonNull(input: String?): String {
        return input!!.trim()
    }

    /**
     * Elvis operator (?:).
     * Demonstrates: NULL_SAFETY operator
     */
    fun provideDefault(input: String?): String {
        return input ?: "default"
    }

    /**
     * Increment operator.
     * Demonstrates: INCREMENTS operator (i++ -> i--)
     */
    fun countUp(n: Int): Int {
        var count = 0
        for (i in 0 until n) {
            count++
        }
        return count
    }

    /**
     * Decrement operator.
     * Demonstrates: INCREMENTS operator (i-- -> i++)
     */
    fun countDown(n: Int): Int {
        var count = n
        while (count > 0) {
            count--
        }
        return count
    }

    /**
     * Boolean return.
     * Demonstrates: TRUE_RETURNS, FALSE_RETURNS operators
     */
    fun isEven(n: Int): Boolean {
        return n % 2 == 0
    }

    /**
     * Void method call.
     * Demonstrates: VOID_METHOD_CALLS operator
     */
    var lastOperation: String = ""

    fun performAdd(a: Int, b: Int): Int {
        lastOperation = "add"
        return a + b
    }

    /**
     * Constructor call.
     * Demonstrates: CONSTRUCTOR_CALLS operator
     */
    fun createUser(name: String): User {
        return User(name = name, age = 0, email = "")
    }

    /**
     * Non-void method call.
     * Demonstrates: NON_VOID_METHOD_CALLS operator
     */
    fun processWithHelper(a: Int, b: Int): Int {
        val result = add(a, b)  // Non-void call that can be removed
        return multiply(result, 2)
    }
}

/**
 * Suppressed class - mutations should be skipped.
 * Demonstrates: @SuppressMutations annotation
 */
@SuppressMutations(reason = "Generated code, no need for mutation testing")
class GeneratedCode {
    fun generate(): String = "generated"
}

/**
 * Suppressed method - mutations should be skipped.
 * Demonstrates: @SuppressMutations on specific method
 */
class PartialSuppression {
    @SuppressMutations(operators = ["ARITHMETIC"], reason = "Trivial getter")
    fun getValue(): Int = 42

    // This method CAN be mutated
    fun calculate(a: Int, b: Int): Int = a + b
}
