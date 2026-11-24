package com.github.rodrigotimoteo.mutkt.application

import kotlin.time.Duration

/** Settings of one analysis run; [timeout] is the per-mutant deadline. */
public data class AnalysisConfig(
    val filters: ClassFilters,
    val timeout: Duration,
    val parallelism: Int,
) {
    init {
        require(timeout.isPositive()) { "timeout must be positive: $timeout" }
        require(parallelism >= 1) { "parallelism must be at least 1: $parallelism" }
    }
}
