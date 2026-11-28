package com.github.rodrigotimoteo.mutkt.application.port

import kotlin.time.Duration

/**
 * The probes the baseline reached, per test in run order: each test class, then each of its test methods when its
 * engine names them; [global] were hit outside any test class (discovery, engine start), so no test class owns them,
 * and [globalReach] names the probed classes entered there.
 */
public data class Coverage(
    val tests: List<TestCoverage>,
    val global: Set<Probe>,
    val globalReach: Set<String> = emptySet(),
)

/**
 * The site probes [test] reached (for a class with methods listed, those outside any of them), how long it ran (a
 * class: its methods included) and the probed classes it [reached] (entered any method of; a class: its methods' too,
 * so the result history keys on class entries alone).
 */
public data class TestCoverage(
    val test: TestId,
    val hits: Set<Probe>,
    val elapsed: Duration,
    val reached: Set<String> = emptySet(),
)
