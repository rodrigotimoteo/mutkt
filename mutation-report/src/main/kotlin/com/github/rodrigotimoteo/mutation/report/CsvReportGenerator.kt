package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationStatus
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
        data class ClassScore(
            val className: String,
            val total: Int,
            val killed: Int,
            val survived: Int,
            val subsumed: Int,
            val score: Int,
        )

        // Per-class scoring mirrors the global report: the denominator
        // is the class's total mutation count INCLUDING subsumed
        // mutations. The subsumed bucket is reported as its own column
        // so callers can reproduce the global killed percentage without
        // an external lookup.
        val classScores =
            report.results
                .groupBy { it.mutation.className }
                .map { (className, results) ->
                    val total = results.size
                    val killed = results.count { it.isKilled }
                    val survived = results.count { it.isSurvived }
                    val subsumed = results.count { it.status == MutationStatus.SUBSUMED }
                    val score = if (total > 0) (killed * 100) / total else 0

                    ClassScore(className, total, killed, survived, subsumed, score)
                }

        val csv =
            buildString {
                appendLine("class,total_mutations,killed,survived,subsumed,score")
                for (cs in classScores) {
                    appendLine(
                        "${escapeCsv(cs.className)},${cs.total},${cs.killed},${cs.survived},${cs.subsumed},${cs.score}",
                    )
                }
            }

        outputDir.mkdirs()
        val file = File(outputDir, "mutation-summary.csv")
        file.writeText(csv)
        return file
    }

    private fun escapeCsv(text: String): String {
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
