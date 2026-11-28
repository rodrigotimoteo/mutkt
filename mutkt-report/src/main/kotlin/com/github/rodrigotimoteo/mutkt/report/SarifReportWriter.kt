package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import java.net.URI

private const val SARIF_VERSION = "2.1.0"
private const val SARIF_SCHEMA = "https://json.schemastore.org/sarif-2.1.0.json"

private val LOCATION_ORDER: Comparator<Pair<MutantResult, String>> =
    compareBy<Pair<MutantResult, String>> { it.second }
        .thenBy { it.first.mutant.line ?: 0 }
        .thenBy { "${it.first.mutant.id}" }

/**
 * Writes each SURVIVED and NO_COVERAGE mutant to [out] as a result of a SARIF 2.1.0 log, at its source line.
 * [sourcePath] gives a mutant's file relative to the repository root, or null when unknown.
 */
public class SarifReportWriter(
    private val out: Appendable,
    private val toolVersion: String?,
    private val sourcePath: (Mutant) -> String?,
) : ReportWriter {
    override fun write(report: MutationReport) {
        out.append(renderJson(sarif(report))).append('\n')
    }

    private fun sarif(report: MutationReport): Map<String, Any> {
        val located =
            report.results
                .filter { it.status == MutantStatus.SURVIVED || it.status == MutantStatus.NO_COVERAGE }
                .map { it to uri(it) }
                .sortedWith(LOCATION_ORDER)
        val rules = located.map { it.first.mutant.operator.value }.distinct().sorted()
        val driver =
            buildMap {
                put("name", "MutKt")
                toolVersion?.let { put("version", it) }
                put("rules", rules.map { mapOf("id" to it) })
            }
        return mapOf(
            "\$schema" to SARIF_SCHEMA,
            "version" to SARIF_VERSION,
            "runs" to
                listOf(
                    mapOf(
                        "tool" to mapOf("driver" to driver),
                        "results" to located.map { (result, uri) -> result(result, uri, rules) },
                    ),
                ),
        )
    }

    // An unknown file still needs a location, so it falls back to the JSON report's package path.
    private fun uri(result: MutantResult): String =
        URI(null, null, sourcePath(result.mutant) ?: filePath(result), null).toASCIIString()
}

private fun result(
    result: MutantResult,
    uri: String,
    rules: List<String>,
): Map<String, Any> {
    val mutant = result.mutant
    val survived = result.status == MutantStatus.SURVIVED
    val region =
        mutant.line
            ?.takeIf { it > 0 }
            ?.let { mapOf("region" to mapOf("startLine" to it)) }
            .orEmpty()
    val status = if (survived) "Survived" else "No coverage"
    return mapOf(
        "ruleId" to mutant.operator.value,
        "ruleIndex" to rules.indexOf(mutant.operator.value),
        "level" to if (survived) "warning" else "note",
        "message" to mapOf("text" to "$status: ${mutant.description} (${mutant.className}.${mutant.methodName})"),
        "locations" to
            listOf(mapOf("physicalLocation" to mapOf("artifactLocation" to mapOf("uri" to uri)) + region)),
    )
}
