package com.github.rodrigotimoteo.mutation.registry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-local registry for tracking mutation testing state.
 * Enables per-thread mutation control without modifying test code.
 */
object MutationRegistry {
    private val active = AtomicBoolean(false)
    private val threads = ConcurrentHashMap<Long, ThreadState>()

    private val timeoutMs = AtomicLong(30_000L)

    class ThreadState {
        val mutationsEnabled = AtomicBoolean(true)
        var currentMutationIndex = AtomicLong(0)
        val triggeredMutations = mutableSetOf<String>()
    }

    /**
     * Get or create state for current thread.
     */
    fun current(): ThreadState {
        return threads.computeIfAbsent(Thread.currentThread().id) {
            ThreadState()
        }
    }

    /**
     * Enable mutation testing globally.
     */
    fun enable() {
        active.set(true)
    }

    /**
     * Disable mutation testing globally.
     */
    fun disable() {
        active.set(false)
    }

    /**
     * Check if mutation testing is globally active.
     */
    fun isActive(): Boolean = active.get()

    /**
     * Set timeout for mutation execution.
     */
    fun setTimeoutMs(ms: Long) {
        timeoutMs.set(ms)
    }

    /**
     * Get timeout for mutation execution.
     */
    fun getTimeoutMs(): Long = timeoutMs.get()

    /**
     * Check if a mutation has timed out.
     * Returns true if current thread has been running longer than the timeout.
     */
    fun checkTimeout(): Boolean {
        val state = threads[Thread.currentThread().id] ?: return false
        val elapsed = System.currentTimeMillis() - state.currentMutationIndex.get()
        return elapsed > timeoutMs.get()
    }

    /**
     * Check if a specific mutation has been triggered on this thread.
     */
    fun isMutationTriggered(mutationId: String): Boolean {
        return current().triggeredMutations.contains(mutationId)
    }

    /**
     * Mark a mutation as triggered on the current thread.
     */
    fun markTriggered(mutationId: String) {
        current().triggeredMutations.add(mutationId)
    }

    /**
     * Reset all thread state.
     */
    fun reset() {
        threads.clear()
    }

    /**
     * Clean up state for the current thread.
     */
    fun cleanup() {
        threads.remove(Thread.currentThread().id)
    }
}
