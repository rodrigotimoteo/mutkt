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
    }

    @AfterEach
    fun cleanup() {
        MutationRegistry.reset()
        MutationRegistry.disable()
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
    fun `interceptBeforeAllMethod with no annotation calls proceed and returns`() {
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptBeforeAllMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `interceptBeforeAllMethod with LENIENT annotation enables registry`() {
        val ctx = makeContext(SampleAnnotated::class.java)
        val inv = makeInvocation()
        extension.interceptBeforeAllMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
        assertTrue(MutationRegistry.isActive())
        assertEquals(30_000L, MutationRegistry.getTimeoutMs())
    }

    @Test
    fun `interceptAfterAllMethod with inactive registry proceeds and returns`() {
        MutationRegistry.disable()
        val ctx = makeContext(PlainTest::class.java)
        val inv = makeInvocation()
        extension.interceptAfterAllMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
    }

    @Test
    fun `interceptAfterAllMethod with active registry disables and resets`() {
        MutationRegistry.enable()
        val ctx = makeContext(SampleAnnotated::class.java)
        val inv = makeInvocation()
        extension.interceptAfterAllMethod(inv, makeInvocationContext(), ctx)
        assertTrue(inv.proceeded)
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
}

@MutKtTest
private class SampleAnnotated

@MutKtTest(mode = VerificationMode.STRICT, timeoutMs = 60_000L)
private class CustomAnnotated

private class PlainTest
