package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import kotlin.time.Duration

/**
 * Confirms one run's kills: a kill counts only when the killing test class passes on the original classes ([originals]
 * by class name), since one that fails there too is flaky and its failure says nothing about the mutant. Each class is
 * confirmed once, on its first kill, which catches a test that fails alone or every time, not every intermittent one.
 */
internal class KillConfirmation(
    private val executor: TestExecutor,
    private val originals: Map<String, ByteArray>,
) {
    // The lock covers only the lookup; the lazy reruns a class once even when several workers ask at the same time.
    private val flaky = mutableMapOf<TestId, Lazy<Boolean>>()

    /** The killing test when [outcome] is a kill by a flaky test class, else null. */
    fun flakyKiller(
        outcome: Outcome,
        mutant: Mutant,
        deadline: Duration,
    ): String? {
        val killed = outcome as? Outcome.Killed
        val test = killed?.test ?: return null
        val verdict = synchronized(flaky) { flaky.getOrPut(test) { lazy { failsOnOriginal(mutant, deadline, test) } } }
        return killed.by.takeIf { verdict.value }
    }

    // A rerun that cannot run says nothing about the test, so the kill stands; an executor that cannot continue still
    // stops the analysis. With method selection the killing class still reruns whole, not just the selected methods:
    // its verdict then holds for every method, so each class reruns once per analysis instead of once per method set.
    @Suppress("TooGenericExceptionCaught")
    private fun failsOnOriginal(
        mutant: Mutant,
        deadline: Duration,
        test: TestId,
    ): Boolean =
        try {
            executor.run(mutant, originals.getValue(mutant.className), deadline, listOf(test)) is Outcome.Killed
        } catch (e: TestExecutionStopped) {
            throw e
        } catch (_: Exception) {
            false
        }
}
