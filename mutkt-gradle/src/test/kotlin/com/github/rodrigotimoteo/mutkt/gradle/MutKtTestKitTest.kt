package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.calc
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The plugin in a real Gradle build with the configuration cache on, on every Gradle version of the matrix. */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtTestKitTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `runs once, is skipped when nothing changed and reruns after a class change`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp("mutkt { failBelowScore = 100 }")

        val first = project.build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":mutkt")?.outcome, first.output)
        assertEquals(null, first.task(":test"), "mutkt must not run the test task\n${first.output}")
        assertTrue("Mutation report" in first.output && "Score: 100.0%" in first.output, first.output)
        assertTrue("Score: 100.0%" in project.report.readText(), "mutkt.txt")
        val json = project.report.resolveSibling("mutation-report.json").readText()
        assertTrue("\"schemaVersion\": \"2\"" in json && "\"status\": \"Killed\"" in json, json)
        assertTrue("<mutation-test-report-app>" in project.report.resolveSibling("mutation-report.html").readText())
        val sarif = project.report.resolveSibling("mutation-report.sarif").readText()
        assertTrue("\"version\": \"2.1.0\"" in sarif, sarif)
        val version = System.getProperty("mutkt.pluginVersion")
        assertTrue("\"name\": \"MutKt\",\n" + " ".repeat(10) + "\"version\": \"$version\"" in sarif, sarif)

        val second = project.build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":mutkt")?.outcome, second.output)
        assertTrue("Reusing configuration cache" in second.output, second.output)

        project.write("src/main/java/demo/Calc.java", calc("b + a"))
        val third = project.build()
        assertEquals(TaskOutcome.SUCCESS, third.task(":mutkt")?.outcome, third.output)
    }

    @OnGradleVersions
    fun `the summary names the mutants a per-class cap left unevaluated`(version: String) {
        val project = TestKitProject(dir, version)
        val sources =
            mapOf(
                "demo/Calc.java" to
                    "package demo;\npublic class Calc {\n" +
                    "    public int add(int a, int b) { return a + b; }\n" +
                    "    public int sub(int a, int b) { return a - b; }\n}\n",
            )
        project.setUp("", sources)
        project.build()
        val total = Regex("Mutants: (\\d+)").find(project.report.readText())!!.groupValues[1].toInt()

        project.setUp("mutkt { maxMutantsPerClass = 1 }", sources)
        val result = project.build()

        val omitted = total - 1
        val line = "Score: 100.0% (1 evaluated, $omitted omitted by the cap)"
        assertTrue(omitted > 0 && line in project.report.readText(), project.report.readText())
        assertTrue(line in result.output, result.output)
    }

    @OnGradleVersions
    fun `fails below the threshold and keeps failing on the next run`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp(
            "mutkt { failBelowScore = 90 }",
            sources =
                mapOf(
                    "demo/Calc.java" to
                        "package demo;\npublic class Calc {\n" +
                        "    public int add(int a, int b) { return a + b; }\n" +
                        "    public int sub(int a, int b) { return a - b; }\n}\n",
                ),
        )

        repeat(2) {
            val result = project.buildAndFail()
            assertEquals(TaskOutcome.FAILED, result.task(":mutkt")?.outcome, result.output)
            val expected = "Mutation score 50.00% is below the threshold of 90.00% (report: "
            assertTrue(expected in result.output && "mutkt.txt)" in result.output, result.output)
        }
        assertTrue(project.report.isFile)
    }
}
