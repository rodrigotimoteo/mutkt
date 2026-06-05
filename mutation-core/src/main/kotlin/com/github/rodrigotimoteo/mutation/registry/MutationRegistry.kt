package com.github.rodrigotimoteo.mutation.registry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe registry for tracking mutation testing state.
 *
 * Enables per-thread mutation control without modifying test code.
 * Used by [MutKtExtension] and [MutKt] to coordinate mutation tracking
 * across test executions.
 *
 * Thread Safety:
 * - All operations use atomic variables and concurrent collections
 * - Per-class thread tracking prevents cross-test interference
 * - Safe for parallel JUnit execution
 *
 * Example:
 * ```kotlin
 * MutationRegistry.enable()
 * MutationRegistry.markStartTime()
 * // ... run test ...
 * if (MutationRegistry.checkTimeout()) {
 *     // Handle timeout
 * }
 * MutationRegistry.cleanup()
 * ```
 *
 * @see MutKtExtension
 * @see MutKt
 */
object MutationRegistry {
    private val active = AtomicBoolean(false)
    private val threads = ConcurrentHashMap<Long, ThreadState>()
    private val classThreads = ConcurrentHashMap<String, MutableSet<Long>>()

    private val timeoutMs = AtomicLong(30_000L)

    class ThreadState {
        val mutationsEnabled = AtomicBoolean(true)
        val startTimeMs = AtomicLong(0)
        val triggeredMutations = ConcurrentHashMap.newKeySet<String>()
    }

    /**
     * Get or create state for current thread.
     */
    fun current(): ThreadState {
        return threads.computeIfAbsent(Thread.currentThread().threadId()) {
            ThreadState()
        }
    }

    /**
     * Begin tracking mutations for a test class.
     */
    fun beginClass(className: String) {
        classThreads.computeIfAbsent(className) { ConcurrentHashMap.newKeySet() }
            .add(Thread.currentThread().threadId())
    }

    /**
     * Reset state for a specific test class (thread-safe).
     */
    fun resetClass(className: String) {
        classThreads.remove(className)?.forEach { threadId ->
            threads.remove(threadId)
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
        val state = threads[Thread.currentThread().threadId()] ?: return false
        val elapsed = System.currentTimeMillis() - state.startTimeMs.get()
        return elapsed > timeoutMs.get()
    }

    /**
     * Mark the start time for the current mutation.
     */
    fun markStartTime() {
        current().startTimeMs.set(System.currentTimeMillis())
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
        active.set(false)
        threads.clear()
        classThreads.clear()
    }

    /**
     * Clean up state for the current thread.
     */
    fun cleanup() {
        threads.remove(Thread.currentThread().threadId())
    }
}
