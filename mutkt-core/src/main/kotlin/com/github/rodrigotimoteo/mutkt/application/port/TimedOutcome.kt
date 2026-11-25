package com.github.rodrigotimoteo.mutkt.application.port

import kotlin.time.Duration

/** An [outcome] and how long the tests ran, measured from the start of execution as the deadline is. */
public data class TimedOutcome(
    val outcome: Outcome,
    val elapsed: Duration,
)
