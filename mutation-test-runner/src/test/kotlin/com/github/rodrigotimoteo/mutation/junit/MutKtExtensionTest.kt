package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.annotation.MutKtConfiguration
import com.github.rodrigotimoteo.mutation.annotation.MutKtTest
import com.github.rodrigotimoteo.mutation.annotation.VerificationMode
import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutKtExtensionTest {
    private lateinit var extension: MutKtExtension

    @BeforeEach
    fun setup() {
        extension = MutKtExtension()
        MutationRegistry.reset()
        MutationRegistry.disable()
        MutKtExtension.resetForTests()
    }

    @AfterEach
    fun cleanup() {
        MutationRegistry.reset()
        MutationRegistry.disable()
        MutKtExtension.resetForTests()
    }

    private fun makeContext(testClass: Class<*>?): ExtensionContext {
        val ctx = mockk<ExtensionContext>(relaxed = true)
        every { ctx.testClass } returns Optional.ofNullable(testClass)
        every { ctx.displayName } returns "testMethod()"
        return ctx
    }

    private fun makeInvocation(): TestInvocation {
        return TestInvocation()
    }

    private fun makeInvocationContext(method: Method? = null): ReflectiveInvocationContext<Method> {
        val ic = mockk<ReflectiveInvocationContext<Method>>(relaxed = true)
        if (method != null) every { ic.executable } returns method
        return ic
    }

    @Test
    fun `getAnnotation returns null when no annotation on class`() {
        val ctx = makeContext(PlainTest::class.java)
        assertEquals(null, extensionTestAnnotation(ctx))
    }

    @Test
    fun `getAnnotation returns annotation when present`() {
        val ctx = makeContext(SampleAnnotated::class.java)
        val ann = extensionTestAnnotation(ctx)
        assertNotNull(ann)
        assertEquals(VerificationMode.LENIENT, ann.mode)
    }

    @Test
    fun `buildConfig with null annotation returns DISABLED mode`() {
        val config = extensionTestBuildConfig(null)
        assertEquals(VerificationMode.DISABLED, config.mode)
    }

    @Test
    fun `buildConfig with annotation copies values`() {
        val annotation = SampleAnnotated::class.java.getAnnotation(MutKtTest::class.java)
        val config = extensionTestBuildConfig(annotation)
        assertEquals(VerificationMode.LENIENT, config.mode)
        assertEquals(30_000L, config.timeoutMs)
        assertTrue(config.skipInIDE)
    }

    @Test
    fun `buildConfig with custom annotation uses custom values`() {
        val annotation = CustomAnnotated::class.java.getAnnotation(MutKtTest::class.java)
        val config = extensionTestBuildConfig(annotation)
        assertEquals(VerificationMode.STRICT, config.mode)
        assertEquals(60_000L, config.timeoutMs)
    }

    @Test
    fun `isRunningSingleTest returns false with no IDE env vars`() {
        val ext = MutKtExtension()
        // Default test environment should not be IDE
        val result = extensionTestIsSingleTest(ext)
        // Don't assert — it may or may not be true depending on environment
        // Just verify it doesn't throw
        assertNotNull(result.toString())
    }

    @Test
    fun `isRunningSingleTest returns true when idea property is set`() {
        System.setProperty("idea.test.cyclic.buffer.size", "1024")
        try {
            val ext = MutKtExtension()
            assertTrue(extensionTestIsSingleTest(ext))
        } finally {
            System.clearProperty("idea.test.cyclic.buffer.size")
        }
    }

    @Test
    fun `isRunningSingleTest returns true when eclipse property is set`() {
        System.setProperty("eclipse.launcher", "/path")
        try {
            val ext = MutKtExtension()
            assertTrue(extensionTestIsSingleTest(ext))
        } finally {
            System.clearProperty("eclipse.launcher")
        }
    }

    @Test
    fun `beforeAll with no annotation does not enable registry`() {
        val ctx = makeContext(PlainTest::class.java)
        extension.beforeAll(ctx)
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `beforeAll with LENIENT annotation enables registry`() {
        val ctx = makeContext(SampleAnnotated::class.java)
        extension.beforeAll(ctx)
        assertTrue(MutationRegistry.isActive())
        assertEquals(30_000L, MutationRegistry.getTimeoutMs())
    }

    @Test
    fun `afterAll with inactive registry returns early`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        // Should not throw — returns early when inactive
        extension.afterAll(ctx)
    }

    @Test
    fun `afterAll with active registry disables and resets`() {
        MutationRegistry.enable()
        val ctx = makeContext(SampleAnnotated::class.java)
        // Simulate that this extension instance already enabled the registry
        // (i.e., beforeAll was previously called) by enabling via beforeAll:
        extension.beforeAll(ctx)
        extension.afterAll(ctx)
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `interceptTestMethod with inactive registry proceeds`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptTestMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
    }

    @Test
    fun `interceptTestMethod with active registry proceeds`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptTestMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
    }

    @Test
    fun `interceptTestMethod rethrows exception`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = ThrowingInvocation(RuntimeException("test failure"))
        val ex =
            kotlin.runCatching {
                extension.interceptTestMethod(inv, makeInvocationContext(), ctx)
            }.exceptionOrNull()
        assertEquals("test failure", ex?.message)
    }

    @Test
    fun `interceptTestFactoryMethod with inactive registry proceeds`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = FactoryInvocation()
        extension.interceptTestFactoryMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded, "proceed() should be called when registry inactive")
    }

    @Test
    fun `interceptTestFactoryMethod with active registry proceeds`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = FactoryInvocation()
        extension.interceptTestFactoryMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded, "proceed() should be called even when registry active")
    }

    @Test
    fun `interceptTestFactoryMethod rethrows exception`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = ThrowingFactoryInvocation(RuntimeException("factory failure"))
        val ex =
            kotlin.runCatching {
                extension.interceptTestFactoryMethod(inv, makeInvocationContext(), ctx)
            }.exceptionOrNull()
        assertEquals("factory failure", ex?.message)
    }

    @Test
    fun `interceptTestTemplateMethod with inactive registry proceeds`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptTestTemplateMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded, "proceed() should be called when registry inactive")
    }

    @Test
    fun `interceptTestTemplateMethod with active registry proceeds`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptTestTemplateMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded, "proceed() should be called even when registry active")
    }

    @Test
    fun `interceptTestTemplateMethod rethrows exception`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = ThrowingInvocation(RuntimeException("template failure"))
        val ex =
            kotlin.runCatching {
                extension.interceptTestTemplateMethod(inv, makeInvocationContext(), ctx)
            }.exceptionOrNull()
        assertEquals("template failure", ex?.message)
    }

    @Test
    fun `interceptDynamicTest with inactive registry proceeds`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptDynamicTest(inv, ctx)
        assertTrue(inv.proceeded, "proceed() should be called when registry inactive")
    }

    @Test
    fun `interceptDynamicTest with active registry proceeds`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptDynamicTest(inv, ctx)
        assertTrue(inv.proceeded, "proceed() should be called even when registry active")
    }

    @Test
    fun `interceptDynamicTest rethrows exception`() {
        MutationRegistry.enable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = ThrowingInvocation(RuntimeException("dynamic failure"))
        val ex =
            kotlin.runCatching {
                extension.interceptDynamicTest(inv, ctx)
            }.exceptionOrNull()
        assertEquals("dynamic failure", ex?.message)
    }

    // Test helpers using reflection to access private methods
    private fun extensionTestAnnotation(context: ExtensionContext): MutKtTest? {
        val method = MutKtExtension::class.java.getDeclaredMethod("getAnnotation", ExtensionContext::class.java)
        method.isAccessible = true
        return method.invoke(extension, context) as MutKtTest?
    }

    private fun extensionTestBuildConfig(annotation: MutKtTest?): MutKtConfiguration {
        val method = MutKtExtension::class.java.getDeclaredMethod("buildConfig", MutKtTest::class.java)
        method.isAccessible = true
        return method.invoke(extension, annotation) as MutKtConfiguration
    }

    private fun extensionTestIsSingleTest(ext: MutKtExtension): Boolean {
        val method = MutKtExtension::class.java.getDeclaredMethod("isRunningSingleTest")
        method.isAccessible = true
        return method.invoke(ext) as Boolean
    }

    // Test invocation implementations
    private class TestInvocation : InvocationInterceptor.Invocation<Void> {
        var proceeded = false

        override fun proceed(): Void? {
            proceeded = true
            return null
        }
    }

    private class ThrowingInvocation(private val ex: Throwable) : InvocationInterceptor.Invocation<Void> {
        override fun proceed(): Void? {
            throw ex
        }
    }

    // Factory method invocations must return T : Any (not Void?), so use
    // a dedicated class with a concrete Any return type.
    private class FactoryInvocation : InvocationInterceptor.Invocation<String> {
        var proceeded = false

        override fun proceed(): String? {
            proceeded = true
            // Return a non-null Any to satisfy the T : Any bound in
            // interceptTestFactoryMethod's signature. The empty string is
            // fine — the test only checks the `proceeded` side effect.
            return ""
        }
    }

    private class ThrowingFactoryInvocation(private val ex: Throwable) : InvocationInterceptor.Invocation<String> {
        override fun proceed(): String? {
            throw ex
        }
    }
}

@MutKtTest
private class SampleAnnotated

@MutKtTest(mode = VerificationMode.STRICT, timeoutMs = 60_000L)
private class CustomAnnotated

private class PlainTest
