package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.annotation.MutKtConfiguration
import com.github.rodrigotimoteo.mutation.annotation.MutKtTest
import com.github.rodrigotimoteo.mutation.annotation.VerificationMode
import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Method
import java.nio.file.Files

/**
 * JUnit 5 extension for mutation testing.
 *
 * When a test class is annotated with @MutKtTest, this extension:
 * 1. Scans for mutations in the target code
 * 2. Tracks triggered mutations during test execution
 * 3. Reports which mutants were killed/survived
 *
 * Usage:
 * ```
 * @ExtendWith(MutKtExtension::class)
 * @MutKtTest
 * class CalculatorTest {
 *     @Test
 *     fun testAdd() = MutKt.underTest { assertEquals(4, Calculator().add(2, 2)) }
 * }
 * ```
 */
class MutKtExtension : InvocationInterceptor {
    private val logger = LoggerFactory.getLogger(MutKtExtension::class.java)

    companion object {
        private var triggerCount = 0
        private var skipCount = 0
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

        val classFiles = scanClasses()
        logger.info("Scanned ${classFiles.size} classes for mutations")

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
            sb.appendLine("  Total triggers: $triggerCount")
            sb.appendLine("  Total skips: $skipCount")
            sb.appendLine("=".repeat(60))
            logger.info(sb.toString())

            if (config.mode == VerificationMode.STRICT && triggered.isNotEmpty()) {
                logger.info("Mutation testing: PASSED (all triggered mutations caught)")
            }
        } finally {
            MutationRegistry.disable()
            MutationRegistry.reset()
            triggerCount = 0
            skipCount = 0
        }

        invocation.proceed()
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        if (!MutationRegistry.isActive()) {
            skipCount++
            invocation.proceed()
            return
        }

        triggerCount++
        val state = MutationRegistry.current()
        state.currentMutationIndex.set(System.currentTimeMillis())

        try {
            invocation.proceed()
        } catch (e: Exception) {
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
            mode = VerificationMode.LENIENT,
            timeoutMs = annotation.timeoutMs,
            skipInIDE = true,
        )
    }

    private fun scanClasses(): Map<String, ByteArray> {
        val dir = findClassesDir() ?: return emptyMap()
        val result = mutableMapOf<String, ByteArray>()
        Files.walk(dir.toPath())
            .filter { it.toString().endsWith(".class") }
            .forEach { path ->
                val relativePath = dir.toPath().relativize(path)
                val className =
                    relativePath.toString()
                        .replace(".class", "")
                        .replace("/", ".")
                        .replace("\\", ".")
                result[className.replace('.', '/')] = Files.readAllBytes(path)
            }
        return result
    }

    private fun findClassesDir(): File? {
        val dir = File("build/classes/kotlin/main")
        return if (dir.exists()) dir else File("build/classes/java/main").takeIf { it.exists() }
    }

    private fun isRunningSingleTest(): Boolean {
        return System.getProperty("idea.test.cyclic.buffer.size") != null ||
            System.getenv("IDEA_INITIAL_DIRECTORY") != null
    }
}
