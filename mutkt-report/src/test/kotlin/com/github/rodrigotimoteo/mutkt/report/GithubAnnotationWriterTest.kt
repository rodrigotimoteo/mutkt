package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GithubAnnotationWriterTest {
    @TempDir
    lateinit var dir: Path

    private fun render(
        report: MutationReport,
        sourcePath: (Mutant) -> String? = { it.sourceFile?.let { name -> "app/src/main/kotlin/demo/$name" } },
    ): List<String> = StringBuilder().also { GithubAnnotationWriter(it, sourcePath).write(report) }.lines().dropLast(1)

    @Test
    fun `each survivor is a warning at its file and line, and a survivor without a file has neither`() {
        assertEquals(
            listOf(
                "::warning file=app/src/main/kotlin/demo/Calc.kt,line=7,title=Survived mutant::" +
                    "negate-conditionals: Negated conditional",
                "::warning file=app/src/main/kotlin/demo/Other.kt,title=Survived mutant::arithmetic: No debug info",
                "::warning file=app/src/main/kotlin/demo/Calc.kt,line=99,title=Survived mutant::" +
                    "arithmetic: Line past the end of the source",
            ),
            render(
                fixtureReport(dir),
                sourcePath = { "app/src/main/kotlin/" + it.className.replace('.', '/') + ".kt" },
            ),
        )
        assertEquals(
            "::warning title=Survived mutant::arithmetic: No debug info",
            render(fixtureReport(dir))[1],
        )
    }

    @Test
    fun `percent signs, line breaks, colons and commas are escaped as workflow commands require`() {
        val report = MutationReport(fixtureReport(dir).results.filter { it.mutant.methodName == "max" })
        val tricky = report.results.map { it.copy(mutant = it.mutant.copy(description = "100% a,b:\r\nc")) }

        assertEquals(
            listOf(
                "::warning file=a%3Ab%2Cc%25.kt,line=7,title=Survived mutant::negate-conditionals: 100%25 a,b:%0D%0Ac",
            ),
            render(report.copy(results = tricky), sourcePath = { "a:b,c%.kt" }),
        )
    }

    @Test
    fun `a line below 1 is left out`() {
        val survivor = fixtureReport(dir).results.first { it.status == MutantStatus.SURVIVED }
        val report = MutationReport(listOf(survivor.copy(mutant = survivor.mutant.copy(line = 0))))

        assertEquals(
            listOf("::warning file=a.kt,title=Survived mutant::negate-conditionals: Negated conditional"),
            render(report, sourcePath = { "a.kt" }),
        )
    }

    @Test
    fun `only the first 50 survivors are annotated, then one line counts the rest`() {
        val survivor = fixtureReport(dir).results.first { it.status == MutantStatus.SURVIVED }
        val report = MutationReport(List(53) { survivor })

        val lines = render(report)

        assertEquals(51, lines.size)
        assertEquals(50, lines.count { it.startsWith("::warning ") })
        assertEquals(
            "::notice title=Survived mutants::3 more survived mutants are not annotated; " +
                "mutation-report.sarif lists them all",
            lines.last(),
        )
    }

    @Test
    fun `a report without survivors writes nothing`() {
        val report = MutationReport(fixtureReport(dir).results.filter { it.status != MutantStatus.SURVIVED })

        assertEquals("", StringBuilder().also { GithubAnnotationWriter(it) { null }.write(report) }.toString())
    }
}
