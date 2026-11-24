package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.MutationReport

/** Publishes a finished report. */
public interface ReportWriter {
    public fun write(report: MutationReport)
}
