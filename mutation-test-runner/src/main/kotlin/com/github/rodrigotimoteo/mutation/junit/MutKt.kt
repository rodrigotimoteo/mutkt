package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.registry.MutationRegistry

/**
 * Entry point for mutation testing in JUnit 5 tests.
 *
 * Provides the `underTest` method that registers code blocks for
 * mutation analysis. When mutation testing is active (enabled via
 * MutKtExtension + @MutKtTest), the current mutation is tracked.
 *
 * Usage:
 * ```
 * @ExtendWith(MutKtExtension::class)
 * @MutKtTest
 * class CalculatorTest {
 *     @Test
 *     fun testAdd() = MutKt.underTest {
 *         val calc = Calculator()
 *         assertEquals(4, calc.add(2, 2))
 *     }
 * }
 * ```
 */
object MutKt {
    /**
     * Register a block of code for mutation testing.
     *
     * When mutation testing is enabled, this tracks which mutations
     * are triggered during execution. When disabled, it's a no-op.
     */
    inline fun <T> underTest(
        mutationId: String = "",
        block: () -> T,
    ): T {
        if (!MutationRegistry.isActive()) return block()

        // Track the mutation if an ID is provided
        if (mutationId.isNotEmpty()) {
            MutationRegistry.markTriggered(mutationId)
        }

        // Execute the block
        val startTime = System.currentTimeMillis()
        try {
            return block()
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > MutationRegistry.getTimeoutMs()) {
                // Timeout will be detected by the extension
            }
        }
    }

    /**
     * Register a block without tracking a specific mutation.
     */
    inline fun <T> underTest(block: () -> T): T {
        return underTest("", block)
    }

    /**
     * Check if mutation testing is currently active.
     */
    fun isActive(): Boolean = MutationRegistry.isActive()

    /**
     * Enable mutation testing programmatically.
     * Usually done automatically by MutKtExtension.
     */
    fun enable() {
        MutationRegistry.enable()
    }

    /**
     * Disable mutation testing programmatically.
     * Usually done automatically by MutKtExtension.
     */
    fun disable() {
        MutationRegistry.disable()
    }

    /**
     * Set the timeout for mutation execution.
     */
    fun setTimeoutMs(ms: Long) {
        MutationRegistry.setTimeoutMs(ms)
    }
}
