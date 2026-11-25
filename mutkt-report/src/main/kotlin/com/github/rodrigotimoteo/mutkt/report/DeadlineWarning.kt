package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
import java.util.Locale
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MILLIS_PER_SECOND = 1000L

/** The warning for a run whose per-mutant deadline [cap] cut, naming the `timeoutMs` that would not cut it. */
public fun deadlineWarning(cap: DeadlineCap): String =
    "Per-mutant deadline capped at timeoutMs (${seconds(cap.timeout)}); the tests took ${seconds(cap.baseline)}, " +
        "so slow mutants may end in TIMEOUT: set timeoutMs to at least ${suggestedTimeoutMs(cap)}"

// Rounded up to whole seconds so the suggestion is readable and still covers the deadline the baseline asked for.
internal fun suggestedTimeoutMs(cap: DeadlineCap): Long = ceil(cap.needed / 1.seconds).toLong() * MILLIS_PER_SECOND

private fun seconds(duration: Duration): String = String.format(Locale.ROOT, "%.1fs", duration / 1.seconds)
