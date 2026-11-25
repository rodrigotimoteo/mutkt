package com.github.rodrigotimoteo.mutkt.gradle

internal data class AnalysisResult(
    val summary: String,
    val score: Double?,
    val mutants: Int,
    val errors: Int,
)
