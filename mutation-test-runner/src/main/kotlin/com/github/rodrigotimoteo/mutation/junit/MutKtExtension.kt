package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.annotation.MutKtConfiguration
import com.github.rodrigotimoteo.mutation.annotation.MutKtTest
import com.github.rodrigotimoteo.mutation.annotation.VerificationMode
import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
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
class MutKtExtension : InvocationInterceptor {
    private val logger = LoggerFactory.getLogger(MutKtExtension::class.java)

    companion object {
        private val triggerCount = AtomicInteger(0)
        private val skipCount = AtomicInteger(0)
    }

    override fun interceptBeforeAllMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val annotation = getAnnotation(extensionContext)
        val config = buildConfig(annotation)

        if (config.mode == VerificationMode.DISABLED) {
            logger.info("Mutation testing: DISABLED")
            invocation.proceed()
            return
        }

        if (isRunningSingleTest() && config.skipInIDE) {
            logger.info("Mutation testing: SKIPPED (single test/IDE)")
            invocation.proceed()
            return
        }

        logger.info("Mutation testing: ENABLED (${config.mode})")
        MutationRegistry.enable()
        MutationRegistry.setTimeoutMs(config.timeoutMs)

        invocation.proceed()
    }

    override fun interceptAfterAllMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        if (!MutationRegistry.isActive()) {
            invocation.proceed()
            return
        }

        val annotation = getAnnotation(extensionContext)
        val config = buildConfig(annotation)

        try {
            val triggered = MutationRegistry.current().triggeredMutations
            val sb = StringBuilder("\n")
            sb.appendLine("=".repeat(60))
            sb.appendLine("  MutKt Mutation Testing Report")
            sb.appendLine("=".repeat(60))
            sb.appendLine("  Triggered mutations: ${triggered.size}")
            sb.appendLine("  Total triggers: ${triggerCount.get()}")
            sb.appendLine("  Total skips: ${skipCount.get()}")
            sb.appendLine("=".repeat(60))
            logger.info(sb.toString())

            if (config.mode == VerificationMode.STRICT && triggered.isNotEmpty()) {
                logger.info("Mutation testing: PASSED (all triggered mutations caught)")
            }
        } finally {
            MutationRegistry.disable()
            MutationRegistry.reset()
            triggerCount.set(0)
            skipCount.set(0)
        }

        invocation.proceed()
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        if (!MutationRegistry.isActive()) {
            skipCount.incrementAndGet()
            invocation.proceed()
            return
        }

        triggerCount.incrementAndGet()
        val state = MutationRegistry.current()
        state.startTimeMs.set(System.currentTimeMillis())

        try {
            invocation.proceed()
        } catch (e: Throwable) {
            if (MutationRegistry.checkTimeout()) {
                logger.warn("Mutation timeout for ${extensionContext.displayName}")
            }
            throw e
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
            skipInIDE = true,
        )
    }

    private fun isRunningSingleTest(): Boolean {
        // IntelliJ IDEA
        if (System.getProperty("idea.test.cyclic.buffer.size") != null) return true
        if (System.getenv("IDEA_INITIAL_DIRECTORY") != null) return true
        // Eclipse
        if (System.getProperty("eclipse.launcher") != null) return true
        // VS Code
        if (System.getenv("VSCODE_CWD") != null) return true
        // Gradle --tests filter
        if (System.getProperty("sun.java.command")?.contains("org.gradle.api.tasks.testing.Test") == true &&
            System.getenv("GRADLE_TEST_FILTER") != null
        ) {
            return true
        }
        return false
    }
}
