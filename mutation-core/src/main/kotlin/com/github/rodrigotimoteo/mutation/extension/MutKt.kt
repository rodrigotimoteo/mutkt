package com.github.rodrigotimoteo.mutation.extension

import com.github.rodrigotimoteo.mutation.engine.MutationRegistry

/**
 * API for explicit mutation testing scoping.
 *
 * Use MutKt.underTest { } to explicitly scope which code should be mutated.
 *
 * Usage:
 * ```kotlin
 * @MutKtTest
 * class CalculatorTest {
 *     @Test
 *     fun `add should return sum`() {
 *         val result = MutKt.underTest { calc.add(2, 3) }
 *         assertEquals(5, result)
 *     }
 * }
 * ```
 */
object MutKt {
    /**
     * Execute a block of code that should be tested against mutations.
     *
     * If a mutation is active (running in a mutation test run), this block
     * will execute with the mutation applied. Otherwise, it runs normally.
     *
     * @param block The code to test
     * @return The result of the block
     */
    fun <T> underTest(block: () -> T): T {
        return block()
    }

    /**
     * Execute a block with explicit mutation testing.
     *
     * @param block The code to test
     * @return The result of the block
     */
    fun <T> mutate(block: () -> T): T {
        return block()
    }

    /**
     * Check if a mutation is currently active.
     */
    fun isMutating(): Boolean = MutationRegistry.isMutationActive()

    /**
     * Get the currently active mutation.
     */
    fun currentMutation() = MutationRegistry.currentMutation()
}
