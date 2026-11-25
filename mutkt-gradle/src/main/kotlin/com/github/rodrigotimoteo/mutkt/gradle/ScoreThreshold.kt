package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import java.util.Locale

internal fun checkThresholdRange(threshold: Int?) {
    if (threshold != null && threshold !in 0..MAX_PERCENT) {
        throw GradleException("failBelowScore must be between 0 and 100 (percent), but is $threshold")
    }
}

internal fun thresholdFailure(
    score: Double?,
    threshold: Int?,
    reportPath: String,
): String? {
    if (score == null || threshold == null || score >= threshold / PERCENT) return null
    return "Mutation score ${percent(score * PERCENT)} is below the threshold of ${percent(threshold.toDouble())} " +
        "(report: $reportPath)"
}

private fun percent(value: Double) = String.format(Locale.ROOT, "%.2f%%", value)

private const val MAX_PERCENT = 100
private const val PERCENT = 100.0
