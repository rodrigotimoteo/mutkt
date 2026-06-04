package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import java.io.File

/**
 * Generates HTML mutation testing reports.
 */
object HtmlReportGenerator {

    fun generate(report: MutationReport, outputDir: File): File {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("  <title>Mutation Test Report</title>")
            appendLine("  <style>")
            appendLine("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; }")
            appendLine("    h1 { color: #333; border-bottom: 2px solid #eee; padding-bottom: 10px; }")
            appendLine("    .summary { display: flex; gap: 20px; margin: 20px 0; }")
            appendLine("    .stat { background: #f5f5f5; padding: 15px; border-radius: 8px; text-align: center; min-width: 100px; }")
            appendLine("    .stat .value { font-size: 24px; font-weight: bold; }")
            appendLine("    .stat .label { color: #666; font-size: 12px; }")
            appendLine("    .killed { color: #22c55e; }")
            appendLine("    .survived { color: #ef4444; }")
            appendLine("    .error { color: #f59e0b; }")
            appendLine("    table { border-collapse: collapse; width: 100%; }")
            appendLine("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
            appendLine("    th { background-color: #f5f5f5; }")
            appendLine("    tr:nth-child(even) { background-color: #fafafa; }")
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <h1>Mutation Test Report</h1>")

            // Summary
            appendLine("  <div class=\"summary\">")
            appendSummaryStat("Total", report.totalMutations.toString(), "")
            appendSummaryStat("Killed", report.killedMutations.toString(), "killed")
            appendSummaryStat("Survived", report.survivedMutations.toString(), "survived")
            appendSummaryStat("Errors", report.errorMutations.toString(), "error")
            appendSummaryStat("No Coverage", report.noCoverageMutations.toString(), "")
            appendSummaryStat("Score", "${report.killedPercentage}%", "")
            appendLine("  </div>")

            // Results table
            appendLine("  <h2>Mutation Results</h2>")
            appendLine("  <table>")
            appendLine("    <tr>")
            appendLine("      <th>ID</th>")
            appendLine("      <th>Operator</th>")
            appendLine("      <th>Class</th>")
            appendLine("      <th>Method</th>")
            appendLine("      <th>Line</th>")
            appendLine("      <th>Status</th>")
            appendLine("    </tr>")

            for (result in report.results) {
                val statusClass = when {
                    result.isKilled -> "killed"
                    result.isSurvived -> "survived"
                    result.status == com.github.rodrigotimoteo.mutation.model.MutationStatus.ERROR -> "error"
                    else -> ""
                }
                appendLine("    <tr>")
                appendLine("      <td>${result.mutation.id}</td>")
                appendLine("      <td>${result.mutation.operator.operatorName}</td>")
                appendLine("      <td>${result.mutation.className}</td>")
                appendLine("      <td>${result.mutation.methodName}</td>")
                appendLine("      <td>${result.mutation.lineNumber}</td>")
                appendLine("      <td class=\"$statusClass\">${result.status}</td>")
                appendLine("    </tr>")
            }

            appendLine("  </table>")
            appendLine("</body>")
            appendLine("</html>")
        }

        val file = File(outputDir, "mutation-report.html")
        file.writeText(html)
        return file
    }

    private fun StringBuilder.appendSummaryStat(label: String, value: String, cssClass: String) {
        appendLine("    <div class=\"stat\">")
        appendLine("      <div class=\"value $cssClass\">$value</div>")
        appendLine("      <div class=\"label\">$label</div>")
        appendLine("    </div>")
    }
}