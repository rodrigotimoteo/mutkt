package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationStatus

/**
 * Generates console-formatted mutation testing reports.
 */
object ConsoleReportGenerator {
    fun generate(report: MutationReport): String {
        return buildString {
            appendLine("=".repeat(60))
            appendLine("Mutation Test Report")
            appendLine("=".repeat(60))
            appendLine("Total mutations:  ${report.totalMutations}")
            appendLine("Killed:           ${report.killedMutations} (${report.killedPercentage}%)")
            appendLine("Survived:         ${report.survivedMutations} (${report.survivedPercentage}%)")
            appendLine("Errors:           ${report.errorMutations}")
            appendLine("Timeouts:         ${report.timeoutMutations}")
            appendLine("No coverage:      ${report.noCoverageMutations}")
            appendLine("Total time:       ${"%.1f".format(report.totalExecutionTimeMs / 1000.0)}s")
            appendLine("=".repeat(60))

            if (report.survivedMutations > 0) {
                appendLine()
                appendLine("Surviving mutants:")
                for (result in report.results.filter { it.isSurvived }) {
                    val m = result.mutation
                    val line = if (m.lineNumber > 0) ":${m.lineNumber}" else ""
                    appendLine("  - [${m.operator.operatorName}] ${m.className}.${m.methodName}$line - ${m.description}")
                }
            }

            if (report.errorMutations > 0) {
                appendLine()
                appendLine("Error mutants:")
                for (result in report.results.filter { it.status == MutationStatus.ERROR }) {
                    val m = result.mutation
                    val line = if (m.lineNumber > 0) ":${m.lineNumber}" else ""
                    val errorMsg = result.errorMessage ?: "unknown error"
                    appendLine(
                        "  - [${m.operator.operatorName}] ${m.className}.${m.methodName}$line - $errorMsg",
                    )
                }
            }
        }
    }
}
