package com.github.rodrigotimoteo.mutation.extension

import com.github.rodrigotimoteo.mutation.engine.VerificationMode

/**
 * Annotation to enable mutation testing for a JUnit 5 test class.
 *
 * When applied, the test class will be run multiple times:
 * 1. Baseline run (no mutations)
 * 2. One run per mutation (each mutation active)
 *
 * If a mutation survives (tests pass when they shouldn't), the test fails.
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
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MutKtTest(
    /**
     * Maximum number of mutations to test.
     * 0 means test all mutations.
     */
    val maxMutations: Int = 0,
    /**
     * Timeout per mutation in milliseconds.
     * If a mutation causes an infinite loop, it will be detected after this timeout.
     */
    val timeout: Long = 30000,
    /**
     * Verification mode for controlling how surviving mutations are handled.
     */
    val verification: VerificationMode = VerificationMode.STRICT,
    /**
     * Classes to include in mutation testing (regex patterns).
     * If empty, all classes are included.
     */
    val includeTargets: Array<String> = [],
    /**
     * Classes to exclude from mutation testing (regex patterns).
     */
    val excludeTargets: Array<String> = [],
)
