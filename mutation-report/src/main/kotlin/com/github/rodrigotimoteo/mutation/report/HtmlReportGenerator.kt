package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import java.io.File

/**
 * Static HTML report generator for mutation testing results.
 *
 * Generates a self-contained HTML file with:
 * - Summary statistics
 * - Kill rate visualization
 * - Per-class mutation breakdown
 * - Detailed mutation results table
 * - Color-coded status indicators
 *
 * No JavaScript frameworks required - pure HTML/CSS.
 *
 * Usage:
 * ```kotlin
 * val generator = HtmlReportGenerator()
 * val reportFile = generator.generate(mutationReport, outputDir)
 * // Open reportFile in browser
 * ```
 */
class HtmlReportGenerator {
    /**
     * Generate HTML mutation report.
     *
     * @param report Mutation report to format
     * @param outputDir Directory to write report
     * @param showClassScores When false, skip the per-class breakdown
     *                        section. Defaults to true so existing
     *                        callers keep their behaviour; the Gradle
     *                        task wires its `showClassScores` extension
     *                        flag through here.
     * @return Generated HTML file
     */
    fun generate(
        report: MutationReport,
        outputDir: File,
        showClassScores: Boolean = true,
    ): File {
        outputDir.mkdirs()
        val outputFile = File(outputDir, "mutation-report.html")

        val html =
            buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html lang=\"en\">")
                appendLine("<head>")
                appendLine("  <meta charset=\"UTF-8\">")
                appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                appendLine("  <title>MutKt Mutation Report</title>")
                appendLine("  <style>")
                appendLine(getCss())
                appendLine("  </style>")
                appendLine("</head>")
                appendLine("<body>")
                appendLine("  <div class=\"container\">")
                appendLine("    <header>")
                appendLine("      <h1>MutKt Mutation Report</h1>")
                appendLine("      <p class=\"subtitle\">")
                appendLine(
                    "        <img src=\"${safeBadgeSrc(report.scoreBadgeUrl)}\" alt=\"Mutation Score\">",
                )
                appendLine("      </p>")
                appendLine("    </header>")
                appendLine()
                appendLine("    <section class=\"summary\">")
                appendLine("      <h2>Summary</h2>")
                appendLine("      <div class=\"stats-grid\">")
                appendLine("        <div class=\"stat-card\">")
                appendLine("          <div class=\"stat-value\">${report.totalMutations}</div>")
                appendLine("          <div class=\"stat-label\">Total Mutations</div>")
                appendLine("        </div>")
                appendLine("        <div class=\"stat-card killed\">")
                appendLine("          <div class=\"stat-value\">${report.killedMutations}</div>")
                appendLine("          <div class=\"stat-label\">Killed (${report.killedPercentage}%)</div>")
                appendLine("        </div>")
                appendLine("        <div class=\"stat-card survived\">")
                appendLine("          <div class=\"stat-value\">${report.survivedMutations}</div>")
                appendLine("          <div class=\"stat-label\">Survived (${report.survivedPercentage}%)</div>")
                appendLine("        </div>")
                appendLine("        <div class=\"stat-card no-coverage\">")
                appendLine("          <div class=\"stat-value\">${report.noCoverageMutations}</div>")
                appendLine("          <div class=\"stat-label\">No Coverage</div>")
                appendLine("        </div>")
                appendLine("        <div class=\"stat-card error\">")
                appendLine("          <div class=\"stat-value\">${report.errorMutations}</div>")
                appendLine("          <div class=\"stat-label\">Errors</div>")
                appendLine("        </div>")
                appendLine("        <div class=\"stat-card timeout\">")
                appendLine("          <div class=\"stat-value\">${report.timeoutMutations}</div>")
                appendLine("          <div class=\"stat-label\">Timeouts</div>")
                appendLine("        </div>")
                appendLine("      </div>")
                appendLine()
                appendLine("      <div class=\"kill-rate-bar\">")
                appendLine("        <div class=\"bar-fill\" style=\"width: ${report.killedPercentage}%\"></div>")
                appendLine("        <span class=\"bar-label\">Kill Rate: ${report.killedPercentage}%</span>")
                appendLine("      </div>")
                appendLine("      <p class=\"execution-time\">Execution Time: ${report.totalExecutionTimeMs / 1000.0}s</p>")
                appendLine("    </section>")
                appendLine()

                // Per-class breakdown
                val classScores = if (showClassScores) report.getClassScores() else emptyList()
                if (classScores.isNotEmpty()) {
                    appendLine("    <section class=\"class-breakdown\">")
                    appendLine("      <h2>Per-Class Breakdown</h2>")
                    appendLine("      <table>")
                    appendLine("        <thead>")
                    appendLine("          <tr>")
                    appendLine("            <th>Class</th>")
                    appendLine("            <th>Total</th>")
                    appendLine("            <th>Killed</th>")
                    appendLine("            <th>Survived</th>")
                    appendLine("            <th>Kill Rate</th>")
                    appendLine("          </tr>")
                    appendLine("        </thead>")
                    appendLine("        <tbody>")
                    classScores.forEach { classScore ->
                        appendLine("          <tr>")
                        appendLine("            <td class=\"class-name\">${escapeHtml(classScore.className)}</td>")
                        appendLine("            <td>${classScore.totalMutations}</td>")
                        appendLine("            <td class=\"killed\">${classScore.killedMutations}</td>")
                        appendLine("            <td class=\"survived\">${classScore.survivedMutations}</td>")
                        appendLine("            <td>")
                        appendLine("              <div class=\"mini-bar\">")
                        appendLine("                <div class=\"mini-fill\" style=\"width: ${classScore.score}%\"></div>")
                        appendLine("              </div>")
                        appendLine("              ${classScore.score}%")
                        appendLine("            </td>")
                        appendLine("          </tr>")
                    }
                    appendLine("        </tbody>")
                    appendLine("      </table>")
                    appendLine("    </section>")
                    appendLine()
                }

                // Detailed results
                appendLine("    <section class=\"results\">")
                appendLine("      <h2>Detailed Results</h2>")
                appendLine("      <table>")
                appendLine("        <thead>")
                appendLine("          <tr>")
                appendLine("            <th>Class</th>")
                appendLine("            <th>Method</th>")
                appendLine("            <th>Line</th>")
                appendLine("            <th>Operator</th>")
                appendLine("            <th>Source</th>")
                appendLine("            <th>Status</th>")
                appendLine("            <th>Time (ms)</th>")
                appendLine("          </tr>")
                appendLine("        </thead>")
                appendLine("        <tbody>")
                report.results.sortedBy { it.mutation.className }.forEach { result ->
                    val statusClass =
                        when (result.status) {
                            MutationStatus.KILLED -> "killed"
                            MutationStatus.SURVIVED -> "survived"
                            MutationStatus.NO_COVERAGE -> "no-coverage"
                            MutationStatus.ERROR -> "error"
                            MutationStatus.TIMEOUT -> "timeout"
                            MutationStatus.WEAK_KILLED -> "weak-killed"
                            MutationStatus.SUBSUMED -> "subsumed"
                        }
                    val sourceCell =
                        if (result.mutation.sourceCode != null) {
                            "<pre class=\"source-snippet\">${escapeHtml(result.mutation.sourceCode!!)}</pre>"
                        } else {
                            "<span class=\"no-source\">—</span>"
                        }
                    appendLine("          <tr>")
                    appendLine("            <td class=\"class-name\">${escapeHtml(result.mutation.className)}</td>")
                    appendLine("            <td>${escapeHtml(result.mutation.methodName)}</td>")
                    appendLine("            <td>${result.mutation.lineNumber}</td>")
                    appendLine("            <td>${escapeHtml(result.mutation.operator.operatorName)}</td>")
                    appendLine("            <td>$sourceCell</td>")
                    appendLine("            <td class=\"$statusClass\">${result.status}</td>")
                    appendLine("            <td>${result.executionTimeMs}</td>")
                    appendLine("          </tr>")
                }
                appendLine("        </tbody>")
                appendLine("      </table>")
                appendLine("    </section>")
                appendLine()
                appendLine("    <footer>")
                appendLine("      <p>Generated by MutKt - Kotlin Mutation Testing</p>")
                appendLine("    </footer>")
                appendLine("  </div>")
                appendLine("</body>")
                appendLine("</html>")
            }

        outputFile.writeText(html)
        return outputFile
    }

    private fun getCss(): String {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; line-height: 1.6; }
            .container { max-width: 1200px; margin: 0 auto; padding: 2rem; }
            header { text-align: center; margin-bottom: 2rem; }
            h1 { font-size: 2.5rem; color: #2c3e50; margin-bottom: 0.5rem; }
            .subtitle { color: #7f8c8d; }
            h2 { font-size: 1.5rem; color: #2c3e50; margin-bottom: 1rem; border-bottom: 2px solid #3498db; padding-bottom: 0.5rem; }
            .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1rem; margin-bottom: 2rem; }
            .stat-card { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }
            .stat-value { font-size: 2rem; font-weight: bold; color: #2c3e50; }
            .stat-label { color: #7f8c8d; font-size: 0.9rem; }
            .stat-card.killed .stat-value { color: #27ae60; }
            .stat-card.survived .stat-value { color: #e74c3c; }
            .stat-card.no-coverage .stat-value { color: #f39c12; }
            .stat-card.error .stat-value { color: #9b59b6; }
            .stat-card.timeout .stat-value { color: #34495e; }
            .kill-rate-bar { background: #ecf0f1; border-radius: 8px; height: 30px; position: relative; margin-bottom: 1rem; overflow: hidden; }
            .bar-fill { background: linear-gradient(90deg, #27ae60, #2ecc71); height: 100%; transition: width 0.3s; }
            .bar-label { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-weight: bold; color: #2c3e50; }
            .execution-time { text-align: center; color: #7f8c8d; }
            section { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 2rem; }
            table { width: 100%; border-collapse: collapse; }
            th, td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #ecf0f1; }
            th { background: #f8f9fa; font-weight: 600; color: #2c3e50; }
            tr:hover { background: #f8f9fa; }
            .class-name { font-family: monospace; font-size: 0.9rem; }
            .killed { color: #27ae60; font-weight: 600; }
            .survived { color: #e74c3c; font-weight: 600; }
            .no-coverage { color: #f39c12; font-weight: 600; }
            .error { color: #9b59b6; font-weight: 600; }
            .timeout { color: #34495e; font-weight: 600; }
            .weak-killed { color: #1abc9c; font-weight: 600; }
            .subsumed { color: #95a5a6; font-weight: 600; }
            .mini-bar { background: #ecf0f1; border-radius: 4px; height: 8px; width: 100px; display: inline-block; vertical-align: middle; margin-right: 0.5rem; }
            .mini-fill { background: #27ae60; height: 100%; border-radius: 4px; }
            .source-snippet { background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 4px; padding: 0.5rem; font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.8rem; line-height: 1.4; margin: 0; white-space: pre-wrap; word-break: break-all; max-width: 300px; overflow-x: auto; }
            .no-source { color: #bdc3c7; font-style: italic; }
            footer { text-align: center; color: #7f8c8d; margin-top: 2rem; }
            """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("`", "&#96;")

    /**
     * Only allow the badge URL to be used as an `<img src>` when it is an
     * HTTPS URL to the shields.io host. Defensive guard against a future
     * change to [MutationReport.scoreBadgeUrl] accidentally letting an
     * untrusted string reach an HTML attribute (which would be an XSS
     * surface). Falls back to an empty src so the alt text is still shown.
     */
    private fun safeBadgeSrc(url: String): String {
        return if (url.startsWith("https://img.shields.io/")) url else ""
    }
}
