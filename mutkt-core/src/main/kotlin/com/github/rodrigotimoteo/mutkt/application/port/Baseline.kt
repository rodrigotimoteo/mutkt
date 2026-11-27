package com.github.rodrigotimoteo.mutkt.application.port

import kotlin.time.Duration

/** The baseline's [outcome] and run time, as in [TimedOutcome]; [coverage] when it was asked for and could be read. */
public data class Baseline(
    val outcome: Outcome,
    val elapsed: Duration,
    val coverage: Coverage? = null,
)
