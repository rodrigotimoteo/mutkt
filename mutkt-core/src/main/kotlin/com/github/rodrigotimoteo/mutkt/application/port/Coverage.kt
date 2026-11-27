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

/** The probes [test] reached and how long it ran. */
public data class TestCoverage(
    val test: TestId,
    val hits: Set<Probe>,
    val elapsed: Duration,
)
