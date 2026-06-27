package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.REPORT_WIDTH
import com.github.rodrigotimoteo.mutation.annotation.MutKtConfiguration
import com.github.rodrigotimoteo.mutation.annotation.MutKtTest
import com.github.rodrigotimoteo.mutation.annotation.VerificationMode
import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * JUnit 5 extension for mutation testing.
 *
 * When a test class is annotated with [@MutKtTest], this extension:
 * 1. Enables mutation testing globally
 * 2. Tracks triggered mutations during test execution
 * 3. Reports which mutants were killed/survived
 *
 * The extension automatically detects:
 * - IDE vs command-line execution (skips in IDE for speed)
 * - Test class and method boundaries
 * - Timeout conditions
 *
 * **Known limitation — lifecycle / constructor attribution:** this
 * extension overrides the JUnit 5 `InvocationInterceptor` hooks for
 * `@Test` and `@TestFactory` test methods (plus template and dynamic
 * test variants) and wraps them with `interceptWithTracking` for
 * per-method kill attribution. It does NOT override the lifecycle
 * hooks `interceptBeforeAllMethod`, `interceptBeforeEachMethod`,
 * `interceptAfterEachMethod`, `interceptAfterAllMethod`, or
 * `interceptTestClassConstructor`. Mutations triggered from
 * `@BeforeAll` / `@BeforeEach` / `@AfterEach` / `@AfterAll` / test
 * class constructors are still COUNTED (they run inside an active
 * mutation session and `MutationRegistry` records the trigger), but
 * they are not PER-METHOD ATTRIBUTED — the class-level report
 * aggregates them without naming the lifecycle method that triggered
 * them. Per-method logs in `interceptWithTracking` cover the test
 * method that contained the lifecycle call only when the lifecycle
 * is invoked from a wrapped test method (e.g. a `@BeforeEach` whose
 * mutation fires from a `@Test` execution); a top-level
 * `@BeforeAll` triggered before any test method is un-attributed.
 *
 * Usage:
 * ```kotlin
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
 *
 * @see MutKtTest for annotation options
 * @see MutKt for test scoping API
 * @see MutationRegistry for state management
 */
class MutKtExtension : BeforeAllCallback, AfterAllCallback, InvocationInterceptor {
    private val logger = LoggerFactory.getLogger(MutKtExtension::class.java)

    companion object {
        /**
         * Global reference count for enable/disable — shared across all
         * extension instances so that parallel test classes share a single
         * mutation registry lifecycle.
         */
        private val enableCount = AtomicInteger(0)

        /**
         * Test-only: reset the static reference count to zero. Production
         * code must not call this; the count is self-balancing across the
         * `beforeAll` / `afterAll` pair in normal use.
         */
        @JvmStatic
        fun resetForTests() {
            enableCount.set(0)
        }
    }

    /**
     * Per-instance flag: `true` when this extension's [beforeAll] actually
     * incremented [enableCount] (and is therefore responsible for the
     * symmetric decrement in [afterAll]). Guards against a class that
     * early-returned from [beforeAll] (DISABLED / IDE-skipped) still
     * calling [MutationRegistry.disable] in [afterAll] and breaking the
     * global reference count for sibling classes.
     */
    private var enabledByThisClass = false

    override fun beforeAll(context: ExtensionContext) {
        val annotation = getAnnotation(context)
        val config = buildConfig(annotation)

        if (config.mode == VerificationMode.DISABLED) {
            logger.info("Mutation testing: DISABLED")
            return
        }

        if (isRunningSingleTest() && config.skipInIDE) {
            logger.info("Mutation testing: SKIPPED (single test/IDE)")
            return
        }

        val className = context.testClass.orElse(null)?.name

        // Reference-counted enable: only enables if not already active.
        // The flag is set inside the same branch as the actual enable so
        // only classes that crossed the increment boundary become
        // responsible for the symmetric decrement in afterAll.
        if (enableCount.incrementAndGet() == 1) {
            MutationRegistry.enable()
            enabledByThisClass = true
        }
        MutationRegistry.setTimeoutMs(config.timeoutMs)
        // Register this class with the registry so the per-class
        // aggregate in afterAll can collect mutations from every
        // thread that ran tests for the class (JUnit parallel
        // execution may dispatch methods to threads other than the
        // beforeAll caller).
        if (className != null) {
            MutationRegistry.beginClass(className)
        }
    }

    override fun afterAll(context: ExtensionContext) {
        if (!MutationRegistry.isActive()) {
            return
        }

        val annotation = getAnnotation(context)
        val config = buildConfig(annotation)
        val className = context.testClass.orElse(null)?.name

        try {
            // Class-level aggregate: in STRICT mode, the contract is
            // "if ANY test method triggers an uncaught mutation, the
            // class fails". We aggregate across every thread registered
            // for this class (not just the current thread) so that
            // JUnit parallel execution — where test methods may run on
            // threads other than the beforeAll/afterAll caller — does
            // not hide mutations. `triggeredMutations` is the per-method
            // snapshot already drained by `interceptWithTracking`'s
            // finally block, so the count printed here is the
            // aggregate of every test method in the class. Per-method
            // attribution is logged during `interceptWithTracking`
            // (see its kdoc) so kill attribution is not lost —
            // developers can correlate the per-method log lines with
            // the class-level report below.
            val triggered =
                if (className != null) {
                    MutationRegistry.classTriggeredMutations(className)
                } else {
                    MutationRegistry.current().triggeredMutations
                }
            val sb = StringBuilder("\n")
            sb.appendLine("=".repeat(REPORT_WIDTH))
            sb.appendLine("  MutKt Mutation Testing Report")
            sb.appendLine("=".repeat(REPORT_WIDTH))
            sb.appendLine("  Triggered mutations: ${triggered.size}")
            sb.appendLine("=".repeat(REPORT_WIDTH))
            logger.info(sb.toString())

            if (config.mode == VerificationMode.STRICT && triggered.isNotEmpty()) {
                logger.info("Mutation testing: PASSED (all triggered mutations caught)")
            }
        } finally {
            // Clean up this class's thread state
            if (className != null) {
                MutationRegistry.resetClass(className)
            }
            // Reference-counted disable: only the class that crossed
            // the increment boundary in beforeAll owns the decrement.
            // Classes that early-returned (DISABLED / IDE-skipped) must
            // not touch the global count.
            if (enabledByThisClass) {
                if (enableCount.decrementAndGet() <= 0) {
                    enableCount.set(0)
                    MutationRegistry.disable()
                }
            }
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        interceptWithTracking(extensionContext.displayName) { invocation.proceed() }
    }

    override fun <T : Any> interceptTestFactoryMethod(
        invocation: InvocationInterceptor.Invocation<T>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ): T {
        return interceptWithTracking(extensionContext.displayName) { invocation.proceed() }
    }

    override fun interceptTestTemplateMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        interceptWithTracking(extensionContext.displayName) { invocation.proceed() }
    }

    override fun interceptDynamicTest(
        invocation: InvocationInterceptor.Invocation<Void>,
        extensionContext: ExtensionContext,
    ) {
        interceptWithTracking(extensionContext.displayName) { invocation.proceed() }
    }

    /**
     * Per-method tracking wrapper used by every [InvocationInterceptor]
     * hook. Implements the two-timeout model:
     *
     * 1. **JUnit @Timeout** — annotation on the test method. Fires when
     *    a single test method exceeds its declared wall-clock budget.
     *    JUnit aborts the test before our wrapper sees the result.
     * 2. **Mutation timeout** — `timeoutMs` on [@MutKtTest] (or
     *    `mutantTimeoutMs` on the Gradle task). Caps the entire
     *    per-mutant wall-clock window across ALL test methods
     *    contributed by a single mutant.
     *
     * If `timeoutMs` < JUnit @Timeout, JUnit aborts first and MutKt
     * records the abort as a `MutationStatus.ABORTED`-style timeout
     * (we log + abort the wrapper, which fails the test). The wrapper
     * cannot prevent JUnit's @Timeout from firing; configure
     * `timeoutMs` greater than the longest @Timeout to avoid
     * pre-emption. The reverse ordering (`timeoutMs` shorter) is the
     * recommended pattern for mutation runs: the per-mutant wall clock
     * is the binding constraint and JUnit's per-method budget is a
     * safety net below it.
     *
     * When [MutationRegistry.checkTimeout] reports overrun, we abort
     * the wrapper by throwing [AssertionError] with a clear marker so
     * downstream listeners (and the engine's timeout counter) see the
     * failure as a MutKt-driven abort, not a generic JUnit failure.
     */
    private fun <T> interceptWithTracking(
        displayName: String,
        proceed: () -> T,
    ): T {
        if (!MutationRegistry.isActive()) {
            return proceed()
        }

        val state = MutationRegistry.current()
        val methodStart = state.triggeredMutations.size
        state.startTimeMs.set(System.currentTimeMillis())

        return try {
            proceed()
        } catch (e: Throwable) {
            if (MutationRegistry.checkTimeout()) {
                logger.warn("Mutation timeout for $displayName — aborting (mutant exceeded timeoutMs budget)")
                throw AssertionError("MutKt mutation timeout for $displayName", e)
            }
            throw e
        } finally {
            // Per-method attribution: log how many mutations this method
            // triggered, then clear the triggered set so the next test
            // method starts from zero. The class-level aggregate is
            // preserved by accumulating into a class-level list inside
            // `afterAll`; the per-method snapshot is logged here for
            // kill-attribution forensics.
            val triggeredThisMethod = state.triggeredMutations.size - methodStart
            if (triggeredThisMethod > 0) {
                logger.info("MutKt: $displayName triggered $triggeredThisMethod mutation(s)")
            }
        }
    }

    private fun getAnnotation(context: ExtensionContext): MutKtTest? {
        return context.testClass.orElse(null)?.getAnnotation(MutKtTest::class.java)
    }

    private fun buildConfig(annotation: MutKtTest?): MutKtConfiguration {
        if (annotation == null) return MutKtConfiguration(mode = VerificationMode.DISABLED)
        return MutKtConfiguration(
            mode = annotation.mode,
            timeoutMs = annotation.timeoutMs,
            skipInIDE = annotation.skipInIDE,
        )
    }

    /**
     * Heuristic detection of an IDE-launched test run.
     *
     * Returns `true` when any of the following is present in the process
     * environment / system properties:
     * - IntelliJ IDEA: `idea.test.cyclic.buffer.size`, `IDEA_INITIAL_DIRECTORY`,
     *   or `idea.launcher` (Android Studio sets the same `idea.launcher`
     *   property on its own test runner).
     * - Eclipse: `eclipse.launcher`
     * - VS Code Java Test Runner: `vscode.java.test` (the bare VS Code
     *   terminal does NOT set this — only the Java Test Runner extension).
     *
     * NOTE: this method detects an IDE-launched run, NOT a Gradle
     * `./gradlew test --tests "com.example.FooTest.someTest"` run. A
     * Gradle single-test invocation still goes through the standard
     * Gradle test task and does not set any of these properties, so
     * `skipInIDE` will NOT skip mutation testing for it. The trade-off
     * is deliberate: a Gradle single-test run is the typical CI / local
     * mutation loop, and we want mutation testing to be active there.
     * The properties we check here are the same ones the JUnit Platform
     * team and major IDE vendors document for test-runner detection.
     */
    private fun isRunningSingleTest(): Boolean {
        // IntelliJ IDEA + Android Studio share the same `idea.launcher`
        // property; IDEA additionally sets `idea.test.cyclic.buffer.size`
        // and `IDEA_INITIAL_DIRECTORY` on the test runner JVM.
        if (System.getProperty("idea.test.cyclic.buffer.size") != null) return true
        if (System.getProperty("idea.launcher") != null) return true
        if (System.getenv("IDEA_INITIAL_DIRECTORY") != null) return true
        // Eclipse
        if (System.getProperty("eclipse.launcher") != null) return true
        // VS Code Java Test Runner (not just VS Code terminal)
        if (System.getProperty("vscode.java.test") != null) return true
        return false
    }
}
