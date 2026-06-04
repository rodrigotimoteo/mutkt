package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport

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
            appendLine("Total time:       ${report.totalExecutionTimeMs / 1000}s")
            appendLine("=".repeat(60))

            if (report.survivedMutations > 0) {
                appendLine()
                appendLine("Surviving mutants:")
                for (result in report.results.filter { it.isSurvived }) {
                    val m = result.mutation
                    appendLine("  - [${m.operator.operatorName}] ${m.className}.${m.methodName}:${m.lineNumber} - ${m.description}")
                }
            }
        }
    }
}
