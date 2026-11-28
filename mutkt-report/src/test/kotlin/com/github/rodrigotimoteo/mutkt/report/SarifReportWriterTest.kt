package com.github.rodrigotimoteo.mutkt.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SarifReportWriterTest {
    @TempDir
    lateinit var dir: Path

    private val inRepo: (Mutant) -> String? = { mutant -> mutant.sourceFile?.let { "app/src/main/kotlin/demo/$it" } }

    private fun render(
        report: MutationReport,
        version: String? = "1.2.3",
        sourcePath: (Mutant) -> String? = inRepo,
    ): String = StringBuilder().also { SarifReportWriter(it, version, sourcePath).write(report) }.toString()

    private fun run(json: String): JsonNode = ObjectMapper().readTree(json).get("runs").single()

    // The fields GitHub's upload-sarif reads; every result has them all.
    @Test
    fun `the log carries the fields code scanning needs`() {
        val log = ObjectMapper().readTree(render(fixtureReport(dir)))
        val driver = log.get("runs")[0].get("tool").get("driver")
        val results = log.get("runs")[0].get("results")

        assertEquals("2.1.0", log.get("version").asText())
        assertTrue(log.get("\$schema").isTextual)
        assertEquals("MutKt", driver.get("name").asText())
        assertTrue(driver.get("rules").isArray)
        assertEquals(4, results.size())
        results.forEach { result ->
            listOf("ruleId", "level").forEach { assertTrue(result.get(it).isTextual, "$it in $result") }
            assertTrue(result.get("message").get("text").isTextual, result.toString())
            val location = result.get("locations")[0].get("physicalLocation")
            assertTrue(location.get("artifactLocation").get("uri").isTextual, result.toString())
        }
        assertTrue(results[0].findValue("region").get("startLine").isInt)
    }

    @Test
    fun `an empty report has one run without results`() {
        val log = ObjectMapper().readTree(render(MutationReport(emptyList())))

        assertEquals("2.1.0", log.get("version").asText())
        assertEquals(0, run(log.toString()).get("results").size())
    }

    @Test
    fun `one result per survived and uncovered mutant, a warning when survived and a note without coverage`() {
        val results = run(render(fixtureReport(dir))).get("results")

        assertEquals(
            listOf(
                "warning negate-conditionals Survived: Negated conditional (demo.Calc.max)",
                "note return-values No coverage: Replaced return value (demo.Calc.max)",
                "warning arithmetic Survived: Line past the end of the source (demo.Calc.gone)",
                "warning arithmetic Survived: No debug info (demo.Other.run)",
            ),
            results.map {
                it.get("level").asText() + " " + it.get("ruleId").asText() + " " +
                    it.get("message").get("text").asText()
            },
        )
    }

    @Test
    fun `each result points at its line in the file the lookup names, else at its package path`() {
        val locations =
            run(render(fixtureReport(dir))).get("results").map {
                it
                    .get("locations")
                    .single()
                    .get("physicalLocation")
                    .toString()
            }

        assertEquals(
            listOf(
                """{"artifactLocation":{"uri":"app/src/main/kotlin/demo/Calc.kt"},"region":{"startLine":7}}""",
                """{"artifactLocation":{"uri":"app/src/main/kotlin/demo/Calc.kt"},"region":{"startLine":7}}""",
                """{"artifactLocation":{"uri":"app/src/main/kotlin/demo/Calc.kt"},"region":{"startLine":99}}""",
                // No SourceFile and no line: the class path, without a region.
                """{"artifactLocation":{"uri":"demo/Other"}}""",
            ),
            locations,
        )
    }

    @Test
    fun `a path the URI syntax forbids is percent-encoded`() {
        val json = render(fixtureReport(dir), sourcePath = { "my app/src/Ä.kt" })

        val uris = run(json).get("results").map { it.findValue("uri").asText() }.toSet()

        assertEquals(setOf("my%20app/src/%C3%84.kt"), uris)
    }

    @Test
    fun `the driver names MutKt, its version and one rule per reported operator that results index`() {
        val run = run(render(fixtureReport(dir)))
        val driver = run.get("tool").get("driver")
        val rules = driver.get("rules").map { it.get("id").asText() }

        assertEquals("MutKt", driver.get("name").asText())
        assertEquals("1.2.3", driver.get("version").asText())
        assertEquals(listOf("arithmetic", "negate-conditionals", "return-values"), rules)
        run.get("results").forEach {
            assertEquals(it.get("ruleId").asText(), rules[it.get("ruleIndex").asInt()])
        }
    }

    @Test
    fun `an unknown version is left out`() {
        assertNull(run(render(fixtureReport(dir), version = null)).get("tool").get("driver").get("version"))
    }
}
