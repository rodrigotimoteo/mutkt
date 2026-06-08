package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationStatus

/**
 * Console report generator with real-time progress.
 *
 * Generates ASCII progress bar and summary for terminal output.
 * Supports both final report and incremental progress updates.
 *
 * Usage:
 * ```kotlin
 * val reporter = ConsoleReporter()
 * // During testing
 * reporter.progress(current, total, currentMutation)
 * // After testing
 * val report = reporter.generate(mutationReport)
 * println(report)
 * ```
 */
class ConsoleReporter {
    private val startTime = System.currentTimeMillis()

    /**
     * Generate progress line during testing.
     *
     * @param current Current mutation index (1-based)
     * @param total Total mutations to test
     * @param mutationInfo Description of current mutation
     * @return Progress line string
     */
    fun progress(
        current: Int,
        total: Int,
        mutationInfo: String,
    ): String {
        val percentage = current.toDouble() / total * 100
        val barWidth = 30
        val filled = (percentage / 100 * barWidth).toInt()
        val empty = barWidth - filled
        val bar = "█".repeat(filled) + "░".repeat(empty)
        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0

        return "\r[MutKt] [$bar] $current/$total ($percentage%) ${elapsed}s $mutationInfo"
    }

    /**
     * Generate final console report.
     *
     * @param report Mutation report to format
     * @return Formatted console string
     */
    fun generate(report: MutationReport): String {
        val sb = StringBuilder()

        sb.appendLine()
        sb.appendLine("╔══════════════════════════════════════════════════════════════╗")
        sb.appendLine("║                    MutKt Mutation Report                    ║")
        sb.appendLine("╠══════════════════════════════════════════════════════════════╣")
        sb.appendLine("║")

        // Summary stats
        sb.appendLine("║  Total Mutations:    ${report.totalMutations}")
        sb.appendLine("║  Killed:             ${report.killedMutations} (${report.killedPercentage}%)")
        sb.appendLine("║  Survived:           ${report.survivedMutations} (${report.survivedPercentage}%)")
        sb.appendLine("║  No Coverage:        ${report.noCoverageMutations}")
        sb.appendLine("║  Errors:             ${report.errorMutations}")
        sb.appendLine("║  Timeouts:           ${report.timeoutMutations}")
        sb.appendLine("║")
        sb.appendLine("║  Execution Time:     ${report.totalExecutionTimeMs / 1000.0}s")

        if (report.totalMutations > 0 && report.totalExecutionTimeMs > 0) {
            val throughput = report.totalMutations.toDouble() / (report.totalExecutionTimeMs / 1000.0)
            sb.appendLine("║  Throughput:         ${throughput.toLong()} mutations/sec")
        }

        sb.appendLine("║")

        // Kill rate bar
        val killRate = report.killedPercentage
        val barWidth = 40
        val filled = (killRate / 100.0 * barWidth).toInt()
        val empty = barWidth - filled
        val bar = "█".repeat(filled) + "░".repeat(empty)
        sb.appendLine("║  Kill Rate: [$bar] $killRate%")
        sb.appendLine("║")

        // Survived mutations (test gaps)
        if (report.survivedMutations > 0) {
            sb.appendLine("║  ⚠ Survived Mutations (Potential Test Gaps):")
            sb.appendLine("║  ─────────────────────────────────────────────")
            report.results
                .filter { it.status == MutationStatus.SURVIVED }
                .take(10)
                .forEach { result ->
                    sb.appendLine("║    ${result.mutation.className}.${result.mutation.methodName}:${result.mutation.lineNumber}")
                    sb.appendLine("║      Operator: ${result.mutation.operator}")
                }
            if (report.survivedMutations > 10) {
                sb.appendLine("║    ... and ${report.survivedMutations - 10} more")
            }
            sb.appendLine("║")
        }

        // Error mutations
        if (report.errorMutations > 0) {
            sb.appendLine("║  ❌ Error Mutations:")
            sb.appendLine("║  ─────────────────────────────────────────────")
            report.results
                .filter { it.status == MutationStatus.ERROR }
                .take(5)
                .forEach { result ->
                    sb.appendLine("║    ${result.mutation.className}.${result.mutation.methodName}:${result.mutation.lineNumber}")
                    sb.appendLine("║      Error: ${result.errorMessage ?: "Unknown"}")
                }
            sb.appendLine("║")
        }

        sb.appendLine("╚══════════════════════════════════════════════════════════════╝")

        return sb.toString()
    }

    /**
     * Generate real-time update line.
     *
     * @param killed Killed count so far
     * @param survived Survived count so far
     * @param current Current mutation description
     * @return Update line string
     */
    fun update(
        killed: Int,
        survived: Int,
        current: String,
    ): String {
        val total = killed + survived
        val killRate = if (total > 0) killed.toDouble() / total * 100 else 0.0
        return "\r[MutKt] Killed: $killed Survived: $survived Kill Rate: ${String.format("%.1f", killRate)}% | $current"
    }

    /**
     * Clear current line (for progress updates).
     *
     * @return Escape sequence to clear line
     */
    fun clearLine(): String = "\r\u001B[K"
}
