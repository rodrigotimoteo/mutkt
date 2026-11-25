package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The plugin in a real Gradle build with the configuration cache on; JUnit comes from this JVM's jars, no network. */
@Tag("e2e")
class MutKtTestKitTest {
    @TempDir
    lateinit var project: File

    @Test
    fun `runs once, is skipped when nothing changed and reruns after a class change`() {
        setUp("mutkt { failBelowScore = 100 }")

        val first = build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":mutkt")?.outcome, first.output)
        assertEquals(null, first.task(":test"), "mutkt must not run the test task\n${first.output}")
        assertTrue("Mutation report" in first.output && "Score: 100.0%" in first.output, first.output)
        assertTrue("Score: 100.0%" in report().readText(), "mutkt.txt")

        val second = build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":mutkt")?.outcome, second.output)
        assertTrue("Reusing configuration cache" in second.output, second.output)

        file("src/main/java/demo/Calc.java").writeText(calc("b + a"))
        val third = build()
        assertEquals(TaskOutcome.SUCCESS, third.task(":mutkt")?.outcome, third.output)
    }

    @Test
    fun `fails below the threshold and keeps failing on the next run`() {
        setUp("mutkt { failBelowScore = 90 }")
        file("src/main/java/demo/Calc.java").writeText(
            "package demo;\npublic class Calc {\n    public int add(int a, int b) { return a + b; }\n" +
                "    public int sub(int a, int b) { return a - b; }\n}\n",
        )

        repeat(2) {
            val result = runner().buildAndFail()
            assertEquals(TaskOutcome.FAILED, result.task(":mutkt")?.outcome, result.output)
            val expected = "Mutation score 50.00% is below the threshold of 90.00% (report: "
            assertTrue(expected in result.output && "mutkt.txt)" in result.output, result.output)
        }
        assertTrue(report().isFile)
    }

    private fun report() = project.resolve("build/reports/mutkt/mutkt.txt")

    private fun runner() =
        GradleRunner
            .create()
            .withProjectDir(project)
            .withPluginClasspath()
            .withArguments("mutkt", "--configuration-cache", "--console=plain", "--offline", "--stacktrace")

    private fun build(): BuildResult = runner().build()

    private fun setUp(extra: String = "") {
        file("settings.gradle").writeText("rootProject.name = 'sample'\n")
        file("build.gradle").writeText(
            """
            plugins {
                id 'java'
                id 'io.github.rodrigotimoteo.mutkt'
            }
            dependencies {
                testImplementation files(${jars().joinToString { "'$it'" }})
            }
            $extra
            """.trimIndent(),
        )
        file("src/main/java/demo/Calc.java").writeText(calc("a + b"))
        file("src/test/java/demo/CalcTest.java").writeText(
            """
            package demo;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;

            public class CalcTest {
                @Test
                void adds() {
                    Assertions.assertEquals(5, new Calc().add(2, 3));
                }
            }
            """.trimIndent(),
        )
    }

    private fun calc(expression: String) =
        "package demo;\npublic class Calc {\n    public int add(int a, int b) { return $expression; }\n}\n"

    private fun file(path: String): File = project.resolve(path).apply { parentFile.mkdirs() }

    private fun jars(): List<String> =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter { it.isFile && PROJECT_JARS.containsMatchIn(it.name) }
            .map { it.absolutePath }

    private companion object {
        val PROJECT_JARS =
            Regex("^(junit-jupiter|junit-platform-(engine|commons)|opentest4j|apiguardian|kotlin-stdlib)")
    }
}
