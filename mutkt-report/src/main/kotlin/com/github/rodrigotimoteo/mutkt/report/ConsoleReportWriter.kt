package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import java.util.Locale

private const val STATUS_WIDTH = 13
private const val LINE_WIDTH = 4
private const val MAX_ERRORS = 10
private const val MAX_METHODS = 10
private const val MAX_SURVIVORS_PER_METHOD = 5
private const val MAX_TESTS = 3
private const val PERCENT = 100

/** Writes a plain-text summary, per-class table and the survivors grouped by method, with hints, to [out]. */
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
        killSplit(report)?.let { out.appendLine(it) }
        out.appendLine("Score: ${percent(report.score)}${omission(report)}")
        report.deadlineCap?.let { out.appendLine(deadlineWarning(it)) }
        classTable(report)
        survivors(report)
        errors(report)
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
        val groups = survivorsByMethod(report.results)
        if (groups.isEmpty()) return
        val shown = groups.take(MAX_METHODS)
        val rest = groups.drop(MAX_METHODS)
        out.appendLine()
        out.appendLine("Survivors (${groups.sumOf { it.survivors.size }} in ${count(groups.size, "method")})")
        shown.forEach(::method)
        if (rest.isNotEmpty()) {
            val survivors = count(rest.sumOf { it.survivors.size }, "survivor")
            out.appendLine("... and ${count(rest.size, "more method")} ($survivors) in the HTML report")
        }
        hints(shown.flatMap { it.survivors.take(MAX_SURVIVORS_PER_METHOD) })
    }

    private fun method(group: MethodSurvivors) {
        val survivors = count(group.survivors.size, "survivor")
        out.appendLine("${group.method}  $survivors" + tests(group.coveredBy).orEmpty())
        group.survivors.take(MAX_SURVIVORS_PER_METHOD).forEach { survivor ->
            val mutant = survivor.mutant
            val line = mutant.line?.let { "line $it" } ?: "no line"
            out.appendLine("  $line  ${mutant.operator}  ${mutant.description}")
            mutant.line?.let { snippet(mutant, it) }
        }
        val hidden = group.survivors.size - MAX_SURVIVORS_PER_METHOD
        if (hidden > 0) out.appendLine("  ... and $hidden more")
    }

    private fun hints(survivors: List<MutantResult>) {
        val hints =
            survivors
                .map { it.mutant.operator }
                .distinct()
                .sortedBy { it.value }
                .mapNotNull { operator -> operatorHint(operator)?.let { "  $operator: $it" } }
        if (hints.isEmpty()) return
        out.appendLine()
        out.appendLine("Hints")
        hints.forEach { out.appendLine(it) }
    }

    private fun errors(report: MutationReport) {
        val errors = report.results.filter { it.status == MutantStatus.ERROR }
        if (errors.isEmpty()) return
        out.appendLine()
        out.appendLine("Errors (${errors.size})")
        errors.take(MAX_ERRORS).forEach { out.appendLine(listOfNotNull(it.mutant.id, it.detail).joinToString(": ")) }
        if (errors.size > MAX_ERRORS) out.appendLine("... and ${errors.size - MAX_ERRORS} more")
    }

    private fun snippet(
        mutant: Mutant,
        line: Int,
    ) {
        for (n in line - 1..line + 1) {
            val text = sources.lineText(mutant.className, mutant.sourceFile, n) ?: continue
            val marker = if (n == line) ">" else " "
            out.appendLine("    $marker ${n.toString().padStart(LINE_WIDTH)} | $text")
        }
    }

    private fun Appendable.appendLine(text: String = "") {
        append(text).append('\n')
    }
}

// A survivor always had a test run, so an empty list is not expected; it gets no clause rather than an empty one.
private fun tests(coveredBy: List<String>?): String? =
    when {
        coveredBy == null -> ", covered by the whole suite"
        coveredBy.isEmpty() -> null
        coveredBy.size > MAX_TESTS ->
            ", covered by ${coveredBy.take(MAX_TESTS).joinToString(", ")} and ${coveredBy.size - MAX_TESTS} more"
        else -> ", covered by ${coveredBy.joinToString(", ")}"
    }

private fun count(
    n: Int,
    noun: String,
): String = if (n == 1) "1 $noun" else "$n ${noun}s"

private fun percent(score: Double?): String = score?.let { String.format(Locale.ROOT, "%.1f%%", it * PERCENT) } ?: "n/a"

private fun detected(report: MutationReport): String {
    val found = report.results.count { it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMEOUT }
    return "$found/${report.results.count { it.status != MutantStatus.ERROR }}"
}

// A kill without a recorded kind is left out of the split rather than guessed.
private fun killSplit(report: MutationReport): String? {
    val assertions = report.results.count { it.killKind == KillKind.ASSERTION }
    val exceptions = report.results.count { it.killKind == KillKind.EXCEPTION }
    return if (assertions + exceptions == 0) {
        null
    } else {
        "Killed ${assertions + exceptions} (assertions $assertions, exceptions $exceptions)"
    }
}

private fun omission(report: MutationReport): String =
    if (report.omitted == 0) "" else " (${report.results.size} evaluated, ${report.omitted} omitted by the cap)"
