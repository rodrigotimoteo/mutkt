package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport

private const val SCHEMA_VERSION = "2"
private const val HIGH_THRESHOLD = 80
private const val LOW_THRESHOLD = 60

/** The report as a mutation-testing-elements document (schema version 2), ready for [renderJson]. */
internal fun strykerReport(
    report: MutationReport,
    sources: SourceLookup,
): Map<String, Any?> =
    buildMap {
        put("schemaVersion", SCHEMA_VERSION)
        put("thresholds", mapOf("high" to HIGH_THRESHOLD, "low" to LOW_THRESHOLD))
        // The schema leaves config free-form; this keeps the cap omission and a capped deadline visible outside the
        // console summary.
        config(report).takeIf { it.isNotEmpty() }?.let { put("config", it) }
        put(
            "files",
            report.results
                .groupBy(::filePath)
                .toSortedMap()
                .mapValues { fileResult(it.value, sources) },
        )
        testFiles(report).takeIf { it.isNotEmpty() }?.let { put("testFiles", it) }
    }

// The viewer shows a coveredBy id only when a test defines it. A test class is the unit coverage knows, so each one is
// its own file holding one test.
private fun testFiles(report: MutationReport): Map<String, Any> =
    report.results
        .flatMap { it.coveredBy.orEmpty() }
        .toSortedSet()
        .associateWith { mapOf("tests" to listOf(mapOf("id" to it, "name" to it))) }

private fun config(report: MutationReport): Map<String, Any> =
    buildMap {
        if (report.omitted > 0) put("omittedByCap", report.omitted)
        report.deadlineCap?.let { cap ->
            put("deadlineCappedAtMs", cap.timeout.inWholeMilliseconds)
            put("baselineMs", cap.baseline.inWholeMilliseconds)
            put("suggestedTimeoutMs", suggestedTimeoutMs(cap))
        }
    }

// A class without a SourceFile attribute has no file to show; its path is the class name so it still gets an entry.
private fun filePath(result: MutantResult): String {
    val mutant = result.mutant
    val directory = mutant.className.substringBeforeLast('.', "").replace('.', '/')
    return mutant.sourceFile?.let { if (directory.isEmpty()) it else "$directory/$it" }
        ?: mutant.className.replace('.', '/')
}

private fun fileResult(
    results: List<MutantResult>,
    sources: SourceLookup,
): Map<String, Any?> {
    val first = results.first().mutant
    val source = sources.sourceText(first.className, first.sourceFile).orEmpty()
    val lines = source.split('\n')
    return mapOf(
        "language" to if (first.sourceFile?.endsWith(".java") == true) "java" else "kotlin",
        "source" to source,
        "mutants" to
            results
                .sortedWith(
                    compareBy({ it.mutant.line ?: 0 }, { it.mutant.id.toString() }),
                ).map { mutant(it, lines) },
    )
}

private fun mutant(
    result: MutantResult,
    lines: List<String>,
): Map<String, Any?> {
    val mutant = result.mutant
    return buildMap {
        put("id", mutant.id.toString())
        put("mutatorName", mutant.operator.value)
        put("description", mutant.description)
        put("location", location(mutant.line, lines))
        put("status", status(result.status))
        (result.detail ?: result.killedBy?.let { "Killed by $it" } ?: hint(result))?.let { put("statusReason", it) }
        result.killedBy?.let { put("killedBy", listOf(it)) }
        result.coveredBy?.let { put("coveredBy", it) }
    }
}

// The viewer shows statusReason with the mutant, so a survivor's hint lands there without custom HTML.
private fun hint(result: MutantResult): String? =
    result.mutant.operator
        .takeIf { result.status == MutantStatus.SURVIVED }
        ?.let(::operatorHint)
        ?.replaceFirstChar(Char::uppercaseChar)

// Bytecode only knows the line, so the mutant spans that line's text, at least one column (end is exclusive). A
// missing line, a missing source (one empty line) or a line past the end is clamped so the viewer can place it.
private fun location(
    line: Int?,
    lines: List<String>,
): Map<String, Any> {
    val clamped = (line ?: 1).coerceIn(1, lines.size)
    val text = lines[clamped - 1]
    val start = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) + 1
    return mapOf(
        "start" to mapOf("line" to clamped, "column" to start),
        "end" to mapOf("line" to clamped, "column" to maxOf(start + 1, text.length + 1)),
    )
}

private fun status(status: MutantStatus): String =
    when (status) {
        MutantStatus.KILLED -> "Killed"
        MutantStatus.SURVIVED -> "Survived"
        MutantStatus.TIMEOUT -> "Timeout"
        MutantStatus.NO_COVERAGE -> "NoCoverage"
        MutantStatus.ERROR -> "RuntimeError"
    }
