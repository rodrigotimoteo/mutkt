package com.github.rodrigotimoteo.mutation.annotation

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS

/**
 * Annotation to mark a test class for mutation testing.
 *
 * When combined with MutKtExtension, this annotation activates
 * automatic multi-run execution where each run uses a different mutant.
 *
 * Example:
 * ```
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
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MutKtTest(
    /**
     * Verification mode for mutation testing. Default matches
     * [MutKtConfiguration.mode] (LENIENT) so the two configuration
     * surfaces stay consistent.
     */
    val mode: VerificationMode = VerificationMode.LENIENT,
    /**
     * Timeout in milliseconds for each mutation execution.
     */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /**
     * Whether to skip mutation testing when running in IDE.
     */
    val skipInIDE: Boolean = true,
)
