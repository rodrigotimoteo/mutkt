package com.github.rodrigotimoteo.mutkt.domain

import kotlin.time.Duration

/** The per-mutant deadline was cut to [timeout] although the [baseline] run asked for [needed]. */
public data class DeadlineCap(
    val timeout: Duration,
    val baseline: Duration,
    val needed: Duration,
)
