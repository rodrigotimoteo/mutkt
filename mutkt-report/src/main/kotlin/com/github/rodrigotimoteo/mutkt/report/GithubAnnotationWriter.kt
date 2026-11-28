package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport

private const val MAX_ANNOTATIONS = 50

/**
 * Writes a GitHub Actions `::warning` line for each of the first 50 survivors, then one line counting the rest.
 * [sourcePath] gives a mutant's file relative to the repository root, or null when unknown.
 */
public class GithubAnnotationWriter(
    private val out: Appendable,
    private val sourcePath: (Mutant) -> String?,
) : ReportWriter {
    override fun write(report: MutationReport) {
        val survivors = report.results.filter { it.status == MutantStatus.SURVIVED }.map { it.mutant }
        survivors.take(MAX_ANNOTATIONS).forEach { out.append(annotation(it)).append('\n') }
        val rest = survivors.size - MAX_ANNOTATIONS
        if (rest > 0) {
            out.append("::notice title=Survived mutants::$rest more survived mutants are not annotated; ")
            out.append("mutation-report.sarif lists them all\n")
        }
    }

    private fun annotation(mutant: Mutant): String {
        val location =
            sourcePath(mutant)?.let { file ->
                "file=${property(file)}," +
                    mutant.line
                        ?.takeIf { it > 0 }
                        ?.let { "line=$it," }
                        .orEmpty()
            }
        return "::warning ${location.orEmpty()}title=Survived mutant::" +
            data(mutant.operator.value + ": " + mutant.description)
    }
}

// GitHub's workflow command escapes: the message escapes '%', CR and LF; a property also ':' and ','.
private fun data(text: String): String = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

private fun property(text: String): String = data(text).replace(":", "%3A").replace(",", "%2C")
