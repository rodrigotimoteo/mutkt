package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.Mutation

/**
 * Thread-local registry for tracking active mutation state.
 * Used by MutKt.underTest and MutKtExtension to coordinate mutation runs.
 */
object MutationRegistry {
    private val currentMutation = ThreadLocal<Mutation?>()
    private val startTime = ThreadLocal<Long>()
    private val timeoutMs = ThreadLocal<Long>()

    /**
     * Get the currently active mutation for this thread.
     */
    fun currentMutation(): Mutation? = currentMutation.get()

    /**
     * Start a mutation run with the given mutation.
     */
    fun startMutation(
        mutation: Mutation,
        timeout: Long = 30000,
    ) {
        currentMutation.set(mutation)
        startTime.set(System.currentTimeMillis())
        timeoutMs.set(timeout)
    }

    /**
     * End the current mutation run.
     */
    fun endMutation() {
        currentMutation.remove()
        startTime.remove()
        timeoutMs.remove()
    }

    /**
     * Check if a mutation is currently active.
     */
    fun isMutationActive(): Boolean = currentMutation.get() != null

    /**
     * Check if the current mutation has timed out.
     * Called by injected timeout checks in loop bodies.
     */
    fun checkTimeout() {
        val start = startTime.get() ?: return
        val timeout = timeoutMs.get() ?: return
        val elapsed = System.currentTimeMillis() - start
        if (elapsed > timeout) {
            val mutation = currentMutation.get()
            throw MutationTimedOutException(
                "Mutation caused infinite loop: ${mutation?.description ?: "unknown"} " +
                    "(${mutation?.className}:${mutation?.methodName}:${mutation?.lineNumber})",
            )
        }
    }

    /**
     * Get elapsed time for current mutation.
     */
    fun elapsedMs(): Long {
        val start = startTime.get() ?: return 0
        return System.currentTimeMillis() - start
    }
}

/**
 * Exception thrown when a mutation causes an infinite loop.
 */
class MutationTimedOutException(message: String) : RuntimeException(message)
