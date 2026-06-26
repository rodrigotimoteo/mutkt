package com.github.rodrigotimoteo.mutation.registry

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import java.time.Clock
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

    private val timeoutMs = AtomicLong(DEFAULT_TIMEOUT_MS)

    @Volatile
    private var clock: Clock = Clock.systemUTC()

    class ThreadState {
        val startTimeMs = AtomicLong(0)
        val triggeredMutations = ConcurrentHashMap.newKeySet<String>()
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
     * Begin tracking mutations for a test class.
     */
    fun beginClass(className: String) {
        classThreads.computeIfAbsent(className) { ConcurrentHashMap.newKeySet() }
            .add(Thread.currentThread().id)
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
        val state = threads[Thread.currentThread().id] ?: return false
        val start = state.startTimeMs.get()
        if (start == 0L) return false // Not marked yet
        val elapsed = clock.millis() - start
        return elapsed > timeoutMs.get()
    }

    /**
     * Mark the start time for the current mutation.
     */
    fun markStartTime() {
        current().startTimeMs.set(clock.millis())
    }

    /**
     * Replace the clock used for time-based operations. Intended for tests
     * that need deterministic control over wall-clock time. Pass
     * `Clock.systemUTC()` (or call [reset]) to restore default behaviour.
     */
    fun setClock(newClock: Clock) {
        clock = newClock
    }

    /**
     * Check if a specific mutation has been triggered on this thread.
     * Does NOT create ThreadState as side-effect.
     */
    fun isMutationTriggered(mutationId: String): Boolean {
        val state = threads[Thread.currentThread().id] ?: return false
        return state.triggeredMutations.contains(mutationId)
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
        timeoutMs.set(DEFAULT_TIMEOUT_MS)
        clock = Clock.systemUTC()
    }

    /**
     * Clean up state for the current thread.
     *
     * Removes the current thread from [threads] and from every
     * [classThreads] set. After the remove, prunes any
     * [classThreads] entry that has become empty so the registry does
     * not retain an unbounded set of empty `MutableSet` instances for
     * test classes that will never run again.
     */
    fun cleanup() {
        val threadId = Thread.currentThread().id
        threads.remove(threadId)
        // Remove from every class set, then drop any set that is now
        // empty. Removing the empty sets prevents a slow leak in
        // long-running JVMs that run many test classes.
        val emptyKeys = mutableListOf<String>()
        for ((className, set) in classThreads) {
            set.remove(threadId)
            if (set.isEmpty()) emptyKeys.add(className)
        }
        // Unconditional remove: `classThreads.remove(key, emptySet())` is
        // a no-op because the stored value is the actual
        // `ConcurrentHashMap.KeySetView`, not a freshly-allocated
        // `emptySet()` singleton. Reference comparison fails and the
        // empty set leaks. Use the single-arg `remove(key)` and accept
        // a benign lost-update race: between the `isEmpty()` check and
        // the remove, another thread could re-add itself to the set;
        // we tolerate that and let the next cleanup re-prune.
        for (key in emptyKeys) {
            classThreads.remove(key)
        }
    }

    /**
     * Run [block] and guarantee that the current thread's [cleanup]
     * runs even if [block] throws. Use this at every site that creates
     * a thread to handle a mutation so the registry's thread state
     * does not leak when the worker is killed by a timeout or error.
     */
    fun <T> withCleanup(block: () -> T): T {
        return try {
            block()
        } finally {
            cleanup()
        }
    }
}
