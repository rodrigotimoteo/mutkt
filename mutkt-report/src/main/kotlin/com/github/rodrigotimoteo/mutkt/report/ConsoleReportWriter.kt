package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import java.util.Locale

/** Writes a plain-text summary, per-class table and survivor list to [out]. */
public class ConsoleReportWriter(
    private val out: Appendable,
    private val sources: SourceLookup,
) : ReportWriter {
    override fun write(report: MutationReport) {
        out.appendLine("Mutation report")
        out.appendLine("Mutants: ${report.results.size}")
        MutantStatus.entries.forEach { status ->
            val count = report.results.count { it.status == status }
            out.appendLine("  ${status.name.padEnd(STATUS_WIDTH)}$count")
        }
        out.appendLine("Score: ${percent(report.score)}")
        classTable(report)
        survivors(report)
    }

    private fun classTable(report: MutationReport) {
        if (report.results.isEmpty()) return
        val rows = report.byClass.toSortedMap().map { (name, sub) -> Triple(name, detected(sub), percent(sub.score)) }
        val nameWidth = maxOf("Class".length, rows.maxOf { it.first.length })
        val detectedWidth = maxOf("Detected".length, rows.maxOf { it.second.length })
        val scoreWidth = maxOf("Score".length, rows.maxOf { it.third.length })
        out.appendLine()
        out.appendLine(
            "${"Class".padEnd(nameWidth)}  ${"Detected".padStart(detectedWidth)}  ${"Score".padStart(scoreWidth)}",
        )
        rows.forEach { (name, found, score) ->
            out.appendLine("${name.padEnd(nameWidth)}  ${found.padStart(detectedWidth)}  ${score.padStart(scoreWidth)}")
        }
    }

    private fun survivors(report: MutationReport) {
        val survivors =
            report.results
                .filter { it.status == MutantStatus.SURVIVED }
                .sortedWith(
                    compareBy(
                        { it.mutant.className },
                        { it.mutant.line ?: Int.MAX_VALUE },
                        { it.mutant.id.toString() },
                    ),
                )
        if (survivors.isEmpty()) return
        out.appendLine()
        out.appendLine("Survivors (${survivors.size})")
        survivors.forEach { survivor ->
            val mutant = survivor.mutant
            val location = mutant.line?.let { "${mutant.className}:$it" } ?: mutant.className
            val method = "${mutant.className}.${mutant.methodName}${mutant.methodDescriptor}"
            out.appendLine("$method ($location)  ${mutant.operator}  ${mutant.description}")
            mutant.line?.let { snippet(mutant.className, it) }
        }
    }

    private fun snippet(
        className: String,
        line: Int,
    ) {
        for (n in line - 1..line + 1) {
            val text = sources.lineText(className, n) ?: continue
            val marker = if (n == line) ">" else " "
            out.appendLine("  $marker ${n.toString().padStart(LINE_WIDTH)} | $text")
        }
    }

    private fun detected(report: MutationReport): String {
        val found = report.results.count { it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMEOUT }
        return "$found/${report.results.count { it.status != MutantStatus.ERROR }}"
    }

    private fun percent(score: Double?): String =
        score?.let { String.format(Locale.ROOT, "%.1f%%", it * PERCENT) } ?: "n/a"

    private fun Appendable.appendLine(text: String = "") {
        append(text).append('\n')
    }

    private companion object {
        const val STATUS_WIDTH = 13
        const val LINE_WIDTH = 4
        const val PERCENT = 100
    }
}
