package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import java.io.File

/**
 * Generates CSV reports for mutation testing results.
 */
object CsvReportGenerator {
    /**
     * Generate CSV report from mutation results.
     */
    fun generate(
        report: MutationReport,
        outputDir: File,
    ): File {
        return generateEnhanced(report, emptyMap(), emptySet(), outputDir)
    }

    /**
     * Generate CSV with subsumption and weakness data.
     */
    fun generateEnhanced(
        report: MutationReport,
        subsumedBy: Map<String, String>,
        weakMutations: Set<String>,
        outputDir: File,
    ): File {
        val csv =
            buildString {
                // Header
                appendLine(
                    "mutation_id,status,operator,operator_description,className," +
                        "methodName,lineNumber,description,executionTimeMs," +
                        "strength,subsumedBy,inlined",
                )

                // Data rows
                for (result in report.results) {
                    val mutationId = result.mutation.id
                    val strength = if (mutationId in weakMutations) "WEAK" else "STRONG"
                    val subsumed = subsumedBy[mutationId] ?: ""

                    appendLine(
                        listOf(
                            escapeCsv(mutationId),
                            result.status.name,
                            escapeCsv(result.mutation.operator.operatorName),
                            escapeCsv(result.mutation.operator.description),
                            escapeCsv(result.mutation.className),
                            escapeCsv(result.mutation.methodName),
                            result.mutation.lineNumber.toString(),
                            escapeCsv(result.mutation.description),
                            result.executionTimeMs.toString(),
                            strength,
                            escapeCsv(subsumed),
                            "false",
                        ).joinToString(","),
                    )
                }
            }

        outputDir.mkdirs()
        val file = File(outputDir, "mutations.csv")
        file.writeText(csv)
        return file
    }

    /**
     * Generate summary CSV with per-class scores.
     */
    fun generateSummary(
        report: MutationReport,
        outputDir: File,
    ): File {
        // Reuse the report's cached per-class breakdown. Scoring rule
        // matches the global report: denominator is total mutations
        // (including subsumed); the subsumed column is reported
        // alongside killed/survived so the per-class row reproduces
        // the global killed percentage without an external lookup.
        val csv =
            buildString {
                appendLine("class,total_mutations,killed,survived,subsumed,score")
                for (cs in report.getClassScores()) {
                    appendLine(
                        "${escapeCsv(cs.className)},${cs.totalMutations}," +
                            "${cs.killedMutations},${cs.survivedMutations}," +
                            "${cs.subsumedMutations},${cs.score}",
                    )
                }
            }

        outputDir.mkdirs()
        val file = File(outputDir, "mutation-summary.csv")
        file.writeText(csv)
        return file
    }

    private fun escapeCsv(text: String): String {
        // Guard against CSV formula injection when opened in Excel/Sheets.
        if (text.isNotEmpty() && text[0] in "=+-@\t\r") return "'" + text
        return if (
            text.contains(",") ||
            text.contains("\"") ||
            text.contains("\n") ||
            text.contains("\r")
        ) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }
}
