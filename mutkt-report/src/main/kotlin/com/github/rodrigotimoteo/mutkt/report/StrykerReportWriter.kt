package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.domain.MutationReport

/**
 * Builds the mutation-testing-elements (Stryker schema 2) document once and writes it as JSON to [json] and as a
 * self-contained HTML page with the viewer to [html].
 */
public class StrykerReportWriter(
    private val json: Appendable,
    private val html: Appendable,
    private val sources: SourceLookup,
) : ReportWriter {
    override fun write(report: MutationReport) {
        val text = renderJson(strykerReport(report, sources))
        json.append(text).append('\n')
        html.append(htmlPage(text))
    }
}
