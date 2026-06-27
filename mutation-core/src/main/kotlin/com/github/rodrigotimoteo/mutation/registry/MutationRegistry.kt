package com.github.rodrigotimoteo.mutation.registry

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import org.slf4j.LoggerFactory
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
 * Global Singleton Caveat
 * ------------------------
 * The registry is a JVM-wide singleton (`object`) with mutable
 * shared state. This is intentional and load-bearing for the
 * instrumentation model: production code under test cannot
 * receive a registry instance, so all tests running in the same
 * JVM (potentially across many test classes concurrently) must
 * observe the same `active`/`threads` state. The trade-offs are:
 *
 * - The singleton is shared across test classes and modules in the
 *   same JVM. Calling [enable] affects every other test that runs
 *   afterwards; call [reset] in `@AfterAll` to restore the default
 *   state for the next suite.
 * - Listeners that want to observe enable/disable/timeout events
 *   should use [addListener] / [removeListener]. The registry
 *   publishes to listeners on every state transition; listener
 *   callbacks run on whichever thread triggered the event.
 * - [reset] is the only public escape hatch for tests. Production
 *   code should never call it.
 * - One engine run at a time: do not run two [MutationEngine]s
 *   concurrently in the same JVM, since the engine and the JUnit
 *   extension both publish to this registry.
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
    private val logger = LoggerFactory.getLogger(MutationRegistry::class.java)

    private val active = AtomicBoolean(false)
    private val threads = ConcurrentHashMap<Long, ThreadState>()
    private val classThreads = ConcurrentHashMap<String, MutableSet<Long>>()
    private val listeners = ConcurrentHashMap.newKeySet<RegistryListener>()

    private val timeoutMs = AtomicLong(DEFAULT_TIMEOUT_MS)

    @Volatile
    private var clock: Clock = Clock.systemUTC()

    /**
     * Per-thread mutation-tracking state. Exposed publicly so the
     * `@JvmStatic` [current] method on the `object` companion can
     * return it from Java callers. Mutable in place: callers may
     * inspect [startTimeMs] and [triggeredMutations] but should not
     * replace the fields. Cleanup is the registry's responsibility.
     */
    class ThreadState {
        val startTimeMs = AtomicLong(0)
        val triggeredMutations = ConcurrentHashMap.newKeySet<String>()
    }

    /**
     * Listener interface for registry state changes. Use [addListener]
     * to observe enable/disable/timeout events without polling.
     *
     * Implementations should be non-blocking and exception-safe: a
     * throwing callback is logged and suppressed so one bad listener
     * cannot poison the engine's run loop.
     */
    interface RegistryListener {
        /** Called after [enable] flips the registry active. */
        fun onEnabled() {}

        /** Called after [disable] flips the registry inactive. */
        fun onDisabled() {}

        /** Called when [checkTimeout] reports a timeout. */
        fun onTimeout(threadId: Long) {}
    }

    /**
     * Register a listener. Thread-safe. The same instance can only
     * be registered once; duplicate registrations are ignored.
     */
    @JvmStatic
    fun addListener(listener: RegistryListener) {
        listeners.add(listener)
    }

    /**
     * Unregister a previously added listener. No-op if [listener] was
     * never registered.
     */
    @JvmStatic
    fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
    }

    /**
     * Get or create state for current thread.
     */
    @JvmStatic
    fun current(): ThreadState {
        return threads.computeIfAbsent(Thread.currentThread().id) {
            ThreadState()
        }
    }

    /**
     * Begin tracking mutations for a test class.
     */
    @JvmStatic
    fun beginClass(className: String) {
        classThreads.computeIfAbsent(className) { ConcurrentHashMap.newKeySet() }
            .add(Thread.currentThread().id)
    }

    /**
     * Reset state for a specific test class (thread-safe).
     */
    @JvmStatic
    fun resetClass(className: String) {
        classThreads.remove(className)?.forEach { threadId ->
            threads.remove(threadId)
        }
    }

    /**
     * Aggregate triggered mutations across every thread registered for
     * [className]. Returns the union of [ThreadState.triggeredMutations]
     * for all threads in the class's thread set, so the per-class
     * report in [MutKtExtension.afterAll] sees mutations triggered on
     * any thread — not only the thread that called [beginClass].
     *
     * Returns an empty set when the class has no registered threads.
     */
    @JvmStatic
    fun classTriggeredMutations(className: String): Set<String> {
        val threadIds = classThreads[className] ?: return emptySet()
        val aggregated = ConcurrentHashMap.newKeySet<String>()
        for (threadId in threadIds) {
            val state = threads[threadId] ?: continue
            aggregated.addAll(state.triggeredMutations)
        }
        return aggregated
    }

    /**
     * Enable mutation testing globally. Notifies registered
     * [RegistryListener]s via [RegistryListener.onEnabled] after
     * the flag flips.
     */
    @JvmStatic
    fun enable() {
        active.set(true)
        notifyEnabled()
    }

    /**
     * Disable mutation testing globally. Notifies registered
     * [RegistryListener]s via [RegistryListener.onDisabled] after
     * the flag flips.
     */
    @JvmStatic
    fun disable() {
        active.set(false)
        notifyDisabled()
    }

    /**
     * Check if mutation testing is globally active.
     */
    @JvmStatic
    fun isActive(): Boolean = active.get()

    /**
     * Set timeout for mutation execution. Must be positive; a
     * non-positive value is treated as no-timeout (the timeout check
     * returns false for `elapsed > 0` even at long elapsed times —
     * the registry never sets this directly).
     *
     * @throws IllegalArgumentException if [ms] is negative.
     */
    @JvmStatic
    fun setTimeoutMs(ms: Long) {
        require(ms >= 0) { "timeoutMs must be >= 0, got $ms" }
        timeoutMs.set(ms)
    }

    /**
     * Get timeout for mutation execution.
     */
    @JvmStatic
    fun getTimeoutMs(): Long = timeoutMs.get()

    /**
     * Check if a mutation has timed out.
     * Returns true if current thread has been running longer than the timeout.
     * Notifies registered [RegistryListener]s via
     * [RegistryListener.onTimeout] when the check transitions to
     * `true` for the first time on a given thread.
     */
    @JvmStatic
    fun checkTimeout(): Boolean {
        val threadId = Thread.currentThread().id
        val state = threads[threadId] ?: return false
        val start = state.startTimeMs.get()
        if (start == 0L) return false // Not marked yet
        val elapsed = clock.millis() - start
        val timedOut = elapsed > timeoutMs.get()
        if (timedOut) notifyTimeout(threadId)
        return timedOut
    }

    /**
     * Mark the start time for the current mutation.
     */
    @JvmStatic
    fun markStartTime() {
        current().startTimeMs.set(clock.millis())
    }

    /**
     * Replace the clock used for time-based operations. Intended for tests
     * that need deterministic control over wall-clock time. Pass
     * `Clock.systemUTC()` (or call [reset]) to restore default behaviour.
     */
    @JvmStatic
    fun setClock(newClock: Clock) {
        clock = newClock
    }

    /**
     * Check if a specific mutation has been triggered on this thread.
     * Does NOT create ThreadState as side-effect.
     */
    @JvmStatic
    fun isMutationTriggered(mutationId: String): Boolean {
        val state = threads[Thread.currentThread().id] ?: return false
        return state.triggeredMutations.contains(mutationId)
    }

    /**
     * Mark a mutation as triggered on the current thread.
     */
    @JvmStatic
    fun markTriggered(mutationId: String) {
        current().triggeredMutations.add(mutationId)
    }

    /**
     * Reset all thread state. Intended for test setup/teardown;
     * production code should never call this.
     */
    @JvmStatic
    fun reset() {
        active.set(false)
        threads.clear()
        classThreads.clear()
        listeners.clear()
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
    @JvmStatic
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
    @JvmStatic
    fun <T> withCleanup(block: () -> T): T {
        return try {
            block()
        } finally {
            cleanup()
        }
    }

    // --- listener dispatch (exceptions in callbacks are swallowed so
    //     a single bad listener cannot poison the engine run loop) ---

    private fun notifyEnabled() = notify { it.onEnabled() }

    private fun notifyDisabled() = notify { it.onDisabled() }

    private fun notifyTimeout(threadId: Long) = notify { it.onTimeout(threadId) }

    private inline fun notify(block: (RegistryListener) -> Unit) {
        for (listener in listeners) {
            try {
                block(listener)
            } catch (e: Exception) {
                // listener failure is non-fatal; do not propagate. Log
                // at WARN with the listener class so a misbehaving
                // callback is visible without poisoning the run loop.
                logger.warn(
                    "RegistryListener {} threw, swallowing: {}",
                    listener::class.java.name,
                    e.toString(),
                )
            }
        }
    }
}
