package com.github.rodrigotimoteo.mutkt.junit

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

// The first FAILED result is kept: the executor stops after the class that has it, so this is the first observed
// failure, not every test that could kill the mutant.
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
        if (result.status == TestExecutionResult.Status.FAILED && killedBy == null) killedBy = label(name(id))
    }

    private fun name(id: TestIdentifier): String =
        when (val source = id.source.orElse(null)) {
            is MethodSource -> "${source.className}.${source.methodName}${invocation(id)}"
            is ClassSource -> source.className
            else -> id.displayName
        }

    // Invocations of a parameterized or dynamic test share the method source: the segment (#2) tells them apart even
    // when custom display names repeat, and the display name keeps the label readable.
    private fun invocation(id: TestIdentifier): String {
        val segment = UniqueId.parse(id.uniqueId).lastSegment
        return if (segment.type in INVOCATION_SEGMENTS) "[${segment.value}] ${id.displayName}" else ""
    }

    // The label ends up in reports: one line, bounded.
    private fun label(name: String) = name.replace(WHITESPACE, " ").trim().take(MAX_LABEL)
}

private val INVOCATION_SEGMENTS = setOf("test-template-invocation", "dynamic-test")

private val WHITESPACE = Regex("\\s+")

private const val MAX_LABEL = 200
