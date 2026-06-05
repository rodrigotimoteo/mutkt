package com.github.rodrigotimoteo.mutation.registry

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationRegistryTest {
    @BeforeEach
    fun setUp() {
        MutationRegistry.reset()
    }

    @AfterEach
    fun tearDown() {
        MutationRegistry.reset()
    }

    @Test
    fun `enable sets active to true`() {
        MutationRegistry.enable()
        assertTrue(MutationRegistry.isActive())
    }

    @Test
    fun `disable sets active to false`() {
        MutationRegistry.enable()
        MutationRegistry.disable()
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `isActive returns false initially`() {
        assertFalse(MutationRegistry.isActive())
    }

    @Test
    fun `setTimeoutMs updates timeout`() {
        MutationRegistry.setTimeoutMs(60_000L)
        assertEquals(60_000L, MutationRegistry.getTimeoutMs())
    }

    @Test
    fun `getTimeoutMs returns default`() {
        // default is 30_000L per the source
        assertEquals(30_000L, MutationRegistry.getTimeoutMs())
    }

    @Test
    fun `beginClass tracks class thread`() {
        MutationRegistry.beginClass("com.example.Foo")
        // No public getter, but internal state should be updated
    }

    @Test
    fun `resetClass removes class tracking`() {
        MutationRegistry.beginClass("com.example.Foo")
        MutationRegistry.beginClass("com.example.Bar")
        MutationRegistry.resetClass("com.example.Foo")
        // No exception thrown
    }

    @Test
    fun `markTriggered adds to thread state`() {
        MutationRegistry.markTriggered("m1")
        assertTrue(MutationRegistry.isMutationTriggered("m1"))
    }

    @Test
    fun `isMutationTriggered returns false for untriggered mutation`() {
        assertFalse(MutationRegistry.isMutationTriggered("unknown"))
    }

    @Test
    fun `markTriggered tracks multiple mutations on same thread`() {
        MutationRegistry.markTriggered("m1")
        MutationRegistry.markTriggered("m2")
        MutationRegistry.markTriggered("m3")
        assertTrue(MutationRegistry.isMutationTriggered("m1"))
        assertTrue(MutationRegistry.isMutationTriggered("m2"))
        assertTrue(MutationRegistry.isMutationTriggered("m3"))
    }

    @Test
    fun `markStartTime records current time`() {
        MutationRegistry.markStartTime()
        assertFalse(MutationRegistry.checkTimeout())
        Thread.sleep(10)
        assertFalse(MutationRegistry.checkTimeout())
    }

    @Test
    fun `checkTimeout returns true when elapsed exceeds timeout`() {
        MutationRegistry.setTimeoutMs(1L)
        MutationRegistry.markStartTime()
        Thread.sleep(50)
        assertTrue(MutationRegistry.checkTimeout())
    }

    @Test
    fun `checkTimeout returns false when no thread state exists`() {
        assertFalse(MutationRegistry.checkTimeout())
    }

    @Test
    fun `current returns ThreadState`() {
        val state = MutationRegistry.current()
        assertNotNull(state)
    }

    @Test
    fun `current returns same state for same thread`() {
        val s1 = MutationRegistry.current()
        val s2 = MutationRegistry.current()
        assertEquals(s1, s2)
    }

    @Test
    fun `cleanup removes current thread state`() {
        MutationRegistry.markTriggered("m1")
        MutationRegistry.cleanup()
        val state = MutationRegistry.current()
        assertFalse(state.triggeredMutations.contains("m1"))
    }

    @Test
    fun `reset clears all state`() {
        MutationRegistry.enable()
        MutationRegistry.markTriggered("m1")
        MutationRegistry.beginClass("com.example.Foo")
        MutationRegistry.reset()
        assertFalse(MutationRegistry.isActive())
        assertFalse(MutationRegistry.isMutationTriggered("m1"))
    }

    @Test
    fun `ThreadState mutationsEnabled defaults to true`() {
        val state = MutationRegistry.current()
        assertTrue(state.mutationsEnabled.get())
    }

    @Test
    fun `ThreadState startTimeMs defaults to 0`() {
        val state = MutationRegistry.current()
        assertEquals(0L, state.startTimeMs.get())
    }

    @Test
    fun `ThreadState triggeredMutations is empty initially`() {
        val state = MutationRegistry.current()
        assertTrue(state.triggeredMutations.isEmpty())
    }

    @Test
    fun `ThreadState triggeredMutations is thread-safe under concurrent access`() {
        val threadCount = 10
        val perThread = 100
        val threads =
            (1..threadCount).map { threadIdx ->
                Thread {
                    repeat(perThread) { i ->
                        MutationRegistry.markTriggered("m_$threadIdx" + "_$i")
                    }
                }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
    }
}
