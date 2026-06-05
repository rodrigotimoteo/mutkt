package com.github.rodrigotimoteo.mutation.junit

import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutKtTest {
    @BeforeEach
    fun resetRegistry() {
        MutationRegistry.reset()
        MutationRegistry.disable()
    }

    @AfterEach
    fun cleanup() {
        MutationRegistry.reset()
        MutationRegistry.disable()
    }

    @Test
    fun `underTest with empty mutationId returns block result when inactive`() {
        val result = MutKt.underTest { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `underTest with empty mutationId returns block result when active`() {
        MutationRegistry.enable()
        val result = MutKt.underTest { "hello" }
        assertEquals("hello", result)
    }

    @Test
    fun `underTest with mutationId marks triggered when active`() {
        MutationRegistry.enable()
        MutKt.underTest("mutation-1") { 10 }
        assertTrue(MutationRegistry.isMutationTriggered("mutation-1"))
    }

    @Test
    fun `underTest with mutationId does not mark when inactive`() {
        MutKt.underTest("mutation-2") { 10 }
        assertFalse(MutationRegistry.isMutationTriggered("mutation-2"))
    }

    @Test
    fun `overload underTest block delegates to mutationId version`() {
        MutationRegistry.enable()
        val result = MutKt.underTest { 99 }
        assertEquals(99, result)
    }

    @Test
    fun `isActive delegates to registry`() {
        assertFalse(MutKt.isActive())
        MutationRegistry.enable()
        assertTrue(MutKt.isActive())
        MutationRegistry.disable()
        assertFalse(MutKt.isActive())
    }

    @Test
    fun `enable delegates to registry`() {
        MutKt.enable()
        assertTrue(MutationRegistry.isActive())
    }

    @Test
    fun `disable delegates to registry`() {
        MutationRegistry.enable()
        MutKt.disable()
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `setTimeoutMs delegates to registry`() {
        MutKt.setTimeoutMs(5_000L)
        assertEquals(5_000L, MutationRegistry.getTimeoutMs())
    }

    @Test
    fun `underTest with type T preserves return type`() {
        val list: List<Int> = MutKt.underTest { listOf(1, 2, 3) }
        assertEquals(3, list.size)
    }

    @Test
    fun `underTest catches exceptions in block`() {
        val exception =
            kotlin.runCatching {
                MutKt.underTest {
                    throw RuntimeException("boom")
                }
            }.exceptionOrNull()
        assertEquals("boom", exception?.message)
    }

    @Test
    fun `underTest block executes even when inactive`() {
        var executed = false
        MutKt.underTest { executed = true }
        assertTrue(executed)
    }
}
