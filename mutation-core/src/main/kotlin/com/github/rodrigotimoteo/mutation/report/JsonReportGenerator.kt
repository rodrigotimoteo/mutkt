package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import java.io.File

/**
 * Generates JSON reports for mutation testing results.
 */
object JsonReportGenerator {
    /**
     * Generate JSON report from mutation results.
     */
    fun generate(
        report: MutationReport,
        outputDir: File,
    ): File {
        val json =
            buildString {
                appendLine("{")
                appendLine("  \"statistics\": {")
                appendLine("    \"mutations\": ${report.totalMutations},")
                appendLine("    \"killed\": ${report.killedMutations},")
                appendLine("    \"survived\": ${report.survivedMutations},")
                appendLine("    \"noCoverage\": ${report.noCoverageMutations},")
                appendLine("    \"timeout\": ${report.timeoutMutations},")
                appendLine("    \"error\": ${report.errorMutations},")
                appendLine("    \"killedPercentage\": ${report.killedPercentage}")
                appendLine("  },")
                appendLine("  \"mutations\": [")
                for ((index, result) in report.results.withIndex()) {
                    appendLine("    {")
                    appendLine("      \"id\": \"${escapeJson(result.mutation.id)}\",")
                    appendLine("      \"status\": \"${result.status}\",")
                    appendLine("      \"operator\": \"${escapeJson(result.mutation.operator.operatorName)}\",")
                    appendLine("      \"operatorDescription\": \"${escapeJson(result.mutation.operator.description)}\",")
                    appendLine("      \"className\": \"${escapeJson(result.mutation.className)}\",")
                    appendLine("      \"methodName\": \"${escapeJson(result.mutation.methodName)}\",")
                    appendLine("      \"lineNumber\": ${result.mutation.lineNumber},")
                    appendLine("      \"description\": \"${escapeJson(result.mutation.description)}\",")
                    appendLine("      \"executionTimeMs\": ${result.executionTimeMs}")
                    if (result.errorMessage != null) {
                        appendLine(",      \"errorMessage\": \"${escapeJson(result.errorMessage)}\"")
                    }
                    if (index < report.results.size - 1) {
                        appendLine("    },")
                    } else {
                        appendLine("    }")
                    }
                }
                appendLine("  ]")
                appendLine("}")
            }

        outputDir.mkdirs()
        val file = File(outputDir, "mutations.json")
        file.writeText(json)
        return file
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
