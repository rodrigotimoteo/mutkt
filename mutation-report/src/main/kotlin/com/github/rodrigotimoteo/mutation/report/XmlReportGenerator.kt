package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import java.io.File

/**
 * Generates XML reports for mutation testing results.
 */
object XmlReportGenerator {
    /**
     * Generate XML report from mutation results.
     */
    fun generate(
        report: MutationReport,
        outputDir: File,
    ): File {
        val xml =
            buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<mutationTestReport>")
                appendLine("  <statistics>")
                appendLine("    <mutations>${report.totalMutations}</mutations>")
                appendLine("    <killed>${report.killedMutations}</killed>")
                appendLine("    <survived>${report.survivedMutations}</survived>")
                appendLine("    <noCoverage>${report.noCoverageMutations}</noCoverage>")
                appendLine("    <timeout>${report.timeoutMutations}</timeout>")
                appendLine("    <error>${report.errorMutations}</error>")
                appendLine("    <killedPercentage>${report.killedPercentage}</killedPercentage>")
                appendLine("  </statistics>")
                appendLine("  <mutations>")
                for (result in report.results) {
                    appendLine("    <mutation>")
                    appendLine("      <id>${escapeXml(result.mutation.id)}</id>")
                    appendLine("      <status>${result.status}</status>")
                    appendLine("      <operator>${escapeXml(result.mutation.operator.operatorName)}</operator>")
                    appendLine("      <operatorDescription>${escapeXml(result.mutation.operator.description)}</operatorDescription>")
                    appendLine("      <className>${escapeXml(result.mutation.className)}</className>")
                    appendLine("      <methodName>${escapeXml(result.mutation.methodName)}</methodName>")
                    appendLine("      <lineNumber>${result.mutation.lineNumber}</lineNumber>")
                    appendLine("      <description>${escapeXml(result.mutation.description)}</description>")
                    appendLine("      <executionTimeMs>${result.executionTimeMs}</executionTimeMs>")
                    val errMsg = result.errorMessage
                    if (errMsg != null) {
                        appendLine("      <errorMessage>${escapeXml(errMsg)}</errorMessage>")
                    }
                    appendLine("    </mutation>")
                }
                appendLine("  </mutations>")
                appendLine("</mutationTestReport>")
            }

        outputDir.mkdirs()
        val file = File(outputDir, "mutations.xml")
        file.writeText(xml)
        return file
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
