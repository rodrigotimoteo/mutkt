package com.github.rodrigotimoteo.mutkt.application.port

/** Thrown by a [TestExecutor] that cannot continue; the analysis stops with [message] instead of scoring ERROR. */
public class TestExecutionStopped(
    message: String,
) : RuntimeException(message)
