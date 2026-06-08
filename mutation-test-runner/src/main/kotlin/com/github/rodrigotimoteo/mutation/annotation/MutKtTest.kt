package com.github.rodrigotimoteo.mutation.annotation

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
     * Verification mode for mutation testing.
     */
    val mode: VerificationMode = VerificationMode.LENIENT,
    /**
     * Timeout in milliseconds for each mutation execution.
     */
    val timeoutMs: Long = 30_000L,
    /**
     * Whether to skip mutation testing when running in IDE.
     */
    val skipInIDE: Boolean = true,
)
