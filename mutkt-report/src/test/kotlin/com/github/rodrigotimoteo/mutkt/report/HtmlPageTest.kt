package com.github.rodrigotimoteo.mutkt.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HtmlPageTest {
    @TempDir
    lateinit var dir: Path

    private fun render(report: MutationReport): String =
        StringBuilder()
            .also {
                StrykerReportWriter(StringBuilder(), it, FileSourceLookup(listOf(dir))).write(report)
            }.toString()

    private val bundle =
        HtmlPageTest::class.java
            .getResource(
                "/com/github/rodrigotimoteo/mutkt/report/mutation-test-elements.js",
            )!!
            .readText()

    @Test
    fun `the page embeds the viewer bundle and the report data`() {
        val html = render(fixtureReport(dir))

        assertTrue(bundle in html, "bundle")
        assertTrue("<mutation-test-report-app></mutation-test-report-app>" in html)
        assertTrue("\"demo/Calc.kt\"" in html)
        assertTrue("Negated conditional" in html)
    }

    @Test
    fun `the page loads nothing from the network`() {
        val html = render(fixtureReport(dir))

        assertEquals(
            emptyList<String>(),
            Regex("<(script|link|img|iframe)\\b[^>]*\\b(src|href)=")
                .findAll(html)
                .map {
                    it.value
                }.toList(),
        )
        assertEquals(2, Regex("<script>").findAll(html).count())
    }

    @Test
    fun `markup in a source file cannot end the data script`() {
        val fixture = fixtureReport(dir)
        Files.writeString(dir.resolve("demo/Calc.kt"), "// </script><script>alert(1)</script><!--\n")

        val html = render(fixture)

        assertEquals(2, Regex("</script>").findAll(html).count())
        val data = html.substringAfter(".report = ").substringBefore(";\n</script>")
        val report = ObjectMapper().readTree(data.replace("\\" + "u003c", "<"))
        assertTrue(
            "</script><script>alert(1)" in
                report
                    .get("files")
                    .get("demo/Calc.kt")
                    .get("source")
                    .asText(),
        )
    }

    @Test
    fun `the page points to the shipped licenses and notices of the bundle`() {
        val html = render(fixtureReport(dir))
        val notices =
            HtmlPageTest::class.java.getResource(
                "/com/github/rodrigotimoteo/mutkt/report/THIRD-PARTY-NOTICES.txt",
            )
        val license =
            HtmlPageTest::class.java.getResource(
                "/com/github/rodrigotimoteo/mutkt/report/mutation-testing-elements.LICENSE",
            )

        assertTrue("THIRD-PARTY-NOTICES.txt" in html && "mutation-testing-elements.LICENSE" in html)
        val text = notices!!.readText()
        listOf(
            "lit 3.3.3",
            "prismjs",
            "tailwindcss",
            "rxjs",
            "anser",
            "fuzziersort",
        ).forEach { assertTrue(it in text, it) }
        assertTrue("Apache License" in license!!.readText())
    }

    @Test
    fun `the bundle cannot end its own script`() {
        assertTrue("</script" !in bundle.lowercase())
        assertTrue("<script" !in bundle.lowercase())
    }
}
