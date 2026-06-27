package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import org.slf4j.LoggerFactory

/**
 * Entry point for mutation testing in JUnit 5 tests.
 *
 * Provides the [underTest] method that registers code blocks for
 * mutation analysis. When mutation testing is active (enabled via
 * [MutKtExtension] + [@MutKtTest]), the current mutation is tracked.
 *
 * Example:
 * ```kotlin
 * @ExtendWith(MutKtExtension::class)
 * @MutKtTest
 * class CalculatorTest {
 *     @Test
 *     fun testAdd() = MutKt.underTest {
 *         val calc = Calculator()
 *         assertEquals(4, calc.add(2, 2))
 *     }
 *
 *     @Test
 *     fun testDivide() = MutKt.underTest("divide-mutation") {
 *         val calc = Calculator()
 *         assertEquals(5, calc.divide(10, 2))
 *     }
 * }
 * ```
 *
 * @see MutKtExtension for automatic test execution
 * @see MutationRegistry for state management
 */
object MutKt {
    private val logger = LoggerFactory.getLogger(MutKt::class.java)

    /**
     * Register a block of code for mutation testing.
     *
     * When mutation testing is enabled, this tracks which mutations
     * are triggered during execution. When disabled, it's a no-op.
     */
    @JvmStatic
    fun <T> underTest(
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
            /**
             * Advisory timeout warning — logged when a test's mutation
             * execution exceeds the configured timeout. Advisory only;
             * does not throw or mark the test as failed. The actual
             * timeout enforcement happens in the engine runner.
             */
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > MutationRegistry.getTimeoutMs()) {
                logger.warn("Mutation execution exceeded timeout: ${elapsed}ms > ${MutationRegistry.getTimeoutMs()}ms")
            }
        }
    }

    /**
     * Register a block without tracking a specific mutation.
     */
    @JvmStatic
    fun <T> underTest(block: () -> T): T {
        return underTest("", block)
    }

    /**
     * Check if mutation testing is currently active.
     */
    @JvmStatic
    fun isActive(): Boolean = MutationRegistry.isActive()

    /**
     * Enable mutation testing programmatically.
     * Usually done automatically by MutKtExtension.
     */
    @JvmStatic
    fun enable() {
        MutationRegistry.enable()
    }

    /**
     * Disable mutation testing programmatically.
     * Usually done automatically by MutKtExtension.
     */
    @JvmStatic
    fun disable() {
        MutationRegistry.disable()
    }

    /**
     * Set the timeout for mutation execution.
     */
    @JvmStatic
    fun setTimeoutMs(ms: Long) {
        MutationRegistry.setTimeoutMs(ms)
    }
}
