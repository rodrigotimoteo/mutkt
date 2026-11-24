package com.github.rodrigotimoteo.mutkt.junit

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

// Only FAILED counts: aborted (assumptions) and skipped (@Disabled) are not kills.
internal class OutcomeListener : TestExecutionListener {
    var started = 0
        private set
    var killedBy: String? = null
        private set

    override fun executionStarted(id: TestIdentifier) {
        if (id.isTest) started++
    }

    override fun executionFinished(
        id: TestIdentifier,
        result: TestExecutionResult,
    ) {
        if (result.status == TestExecutionResult.Status.FAILED && killedBy == null) killedBy = name(id)
    }

    private fun name(id: TestIdentifier): String =
        when (val source = id.source.orElse(null)) {
            is MethodSource -> "${source.className}.${source.methodName}"
            is ClassSource -> source.className
            else -> id.displayName
        }
}
