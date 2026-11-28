package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.Survivor

internal data class AnalysisResult(
    val summary: String,
    val score: Double?,
    val mutants: Int,
    val errors: Int,
    val json: String,
    val html: String,
    val sarif: String,
    val annotations: String,
    // Read only for the survivor baseline, after the reports are written.
    val survivors: () -> List<Survivor>,
)
