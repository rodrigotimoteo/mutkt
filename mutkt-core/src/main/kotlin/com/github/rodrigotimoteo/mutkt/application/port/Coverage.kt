package com.github.rodrigotimoteo.mutkt.application.port

import kotlin.time.Duration

/**
 * The probes the baseline reached, per test class in run order; [global] were hit outside any test class (discovery,
 * engine start), so no test class owns them.
 */
public data class Coverage(
    val tests: List<TestCoverage>,
    val global: Set<Probe>,
)

/**
 * The site probes [test] reached, how long it ran and the probed classes it [reached] (entered any method of), which
 * the result history keys on.
 */
public data class TestCoverage(
    val test: TestId,
    val hits: Set<Probe>,
    val elapsed: Duration,
    val reached: Set<String> = emptySet(),
)
