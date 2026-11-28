package com.github.rodrigotimoteo.mutkt.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class StrykerReportWriterTest {
    @TempDir
    lateinit var dir: Path

    private fun render(report: MutationReport): String =
        StringBuilder()
            .also {
                StrykerReportWriter(it, StringBuilder(), FileSourceLookup(listOf(dir))).write(report)
            }.toString()

    @Test
    fun `the fixture report matches the golden file`() {
        val golden = StrykerReportWriterTest::class.java.getResource("/golden/report.json")!!.readText()

        assertEquals(golden, render(fixtureReport(dir)))
    }

    @Test
    fun `the fixture report validates against the published schema`() {
        assertEquals(emptyList<String>(), SchemaValidator(schemaText()).validate(render(fixtureReport(dir))))
    }

    @Test
    fun `an empty report validates and has no files`() {
        val json = render(MutationReport(emptyList()))

        assertEquals(emptyList<String>(), SchemaValidator(schemaText()).validate(json))
        assertEquals(0, ObjectMapper().readTree(json).get("files").size())
    }

    @Test
    fun `a capped deadline is named in the config next to the cap omission and still validates`() {
        val cap = DeadlineCap(60.seconds, 20.seconds, 84.seconds)
        val json = render(fixtureReport(dir).copy(deadlineCap = cap))

        assertEquals(emptyList<String>(), SchemaValidator(schemaText()).validate(json))
        assertEquals(
            mapOf(
                "omittedByCap" to 2,
                "deadlineCappedAtMs" to 60_000,
                "baselineMs" to 20_000,
                "suggestedTimeoutMs" to 84_000,
            ),
            ObjectMapper().readValue(json, Map::class.java)["config"],
        )
    }

    @Test
    fun `results reused from history are counted in the config and still validate`() {
        val fixture = fixtureReport(dir)
        val report = fixture.copy(results = fixture.results.mapIndexed { i, result -> result.copy(reused = i < 2) })
        val json = render(report)

        assertEquals(emptyList<String>(), SchemaValidator(schemaText()).validate(json))
        assertEquals(
            mapOf("omittedByCap" to 2, "reused" to 2),
            ObjectMapper().readValue(json, Map::class.java)["config"],
        )
    }

    @Test
    fun `coveredBy lists the covering test classes and testFiles defines each one for the viewer`() {
        val json = ObjectMapper().readTree(render(fixtureReport(dir)))
        val coveredBy =
            json.get("files").flatMap { it.get("mutants") }.associate {
                it.get("description").asText() to it.get("coveredBy")?.map(JsonNode::asText)
            }

        assertEquals(listOf("demo.CalcTest"), coveredBy["Replaced + with -"])
        assertEquals(listOf("demo.MaxTest", "demo.CalcTest"), coveredBy["Negated conditional"])
        assertEquals(emptyList<String>(), coveredBy["Replaced return value"])
        assertEquals(null, coveredBy["Replaced + with *"])
        assertEquals(
            """{"demo.CalcTest":{"tests":[{"id":"demo.CalcTest","name":"demo.CalcTest"}]},""" +
                """"demo.MaxTest":{"tests":[{"id":"demo.MaxTest","name":"demo.MaxTest"}]}}""",
            json.get("testFiles").toString(),
        )
    }

    @Test
    fun `a survivor's status reason is the hint of its operator`() {
        val reasons =
            ObjectMapper().readTree(render(fixtureReport(dir))).get("files").flatMap { it.get("mutants") }.associate {
                it.get("description").asText() to it.get("statusReason")?.asText()
            }

        assertEquals("No test checks what happens on both sides of the condition", reasons["Negated conditional"])
        assertEquals("No test asserts on the computed value", reasons["No debug info"])
        assertEquals("Killed by demo.CalcTest.adds() (assertion)", reasons["Replaced + with -"])
        assertEquals(null, reasons["Replaced return value"])
    }

    @Test
    fun `a report without coverage has no testFiles`() {
        val report = fixtureReport(dir).let { r -> r.copy(results = r.results.map { it.copy(coveredBy = null) }) }

        assertEquals(null, ObjectMapper().readTree(render(report)).get("testFiles"))
    }

    @Test
    fun `every status maps to its schema name`() {
        val files = ObjectMapper().readTree(render(fixtureReport(dir))).get("files")
        val statuses =
            files.flatMap { it.get("mutants") }.map { it.get("status").asText() }

        assertEquals(setOf("Killed", "Survived", "Timeout", "NoCoverage", "RuntimeError"), statuses.toSet())
    }

    @Test
    fun `a missing source and a line past the end are clamped to a one-column span`() {
        val files = ObjectMapper().readTree(render(fixtureReport(dir))).get("files")

        assertEquals(
            """{"start":{"line":1,"column":1},"end":{"line":1,"column":2}}""",
            location(files, "demo/Other", "run"),
        )
        assertEquals(
            """{"start":{"line":9,"column":1},"end":{"line":9,"column":2}}""",
            location(files, "demo/Calc.kt", "gone"),
        )
    }

    @Test
    fun `every location lies inside its source and spans at least one column`() {
        val files = ObjectMapper().readTree(render(fixtureReport(dir))).get("files")

        for (file in files) {
            val lineCount =
                file
                    .get("source")
                    .asText()
                    .split('\n')
                    .size
            for (mutant in file.get("mutants")) {
                val (start, end) = mutant.get("location").let { it.get("start") to it.get("end") }
                assertTrue(start.get("line").asInt() in 1..lineCount, mutant.toString())
                assertEquals(start.get("line"), end.get("line"), mutant.toString())
                assertTrue(end.get("column").asInt() > start.get("column").asInt(), mutant.toString())
            }
        }
    }

    @Test
    fun `the document is built once for both files, reading each source once`() {
        val reads = mutableListOf<String>()
        val files = FileSourceLookup(listOf(dir))
        val counting =
            object : SourceLookup by files {
                override fun sourceText(
                    className: String,
                    sourceFile: String?,
                ): String? = files.sourceText(className, sourceFile).also { reads += className }
            }
        val json = StringBuilder()
        val html = StringBuilder()

        StrykerReportWriter(json, html, counting).write(fixtureReport(dir))

        assertEquals(listOf("demo.Calc", "demo.Other"), reads)
        assertTrue(json.toString().trimEnd().replace("<", "\\" + "u003c") in html.toString())
    }

    private fun location(
        files: JsonNode,
        path: String,
        method: String,
    ): String =
        files
            .get(path)
            .get("mutants")
            .first { "#" + method + "(" in it.get("id").asText() }
            .get("location")
            .toString()

    @Test
    fun `source text with control characters and quotes survives a round trip`() {
        val report = fixtureReport(dir).let { MutationReport(it.results.take(1)) }
        val tricky = "val s = \"a\\b\"\t// " + Char(0) + Char(1) + " </script>"
        Files.writeString(dir.resolve("demo/Calc.kt"), tricky + "\n")

        val json = ObjectMapper().readTree(render(report))

        assertEquals(
            tricky,
            json
                .get("files")
                .get("demo/Calc.kt")
                .get("source")
                .asText(),
        )
    }
}
