package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.GATE
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.JUNIT4_GATE_TEST
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.JUNIT4_JARS
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.assertSummaryLines
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** A JUnit 4 project (`useJUnit()`, no JUnit Platform engine of its own) with `engines = ['junit-vintage']`. */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtVintageEngineTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `JUnit 4 tests run on the vintage engine MutKt supplies`(version: String) {
        assertSummaryLines(run(version, ownEngine = false), "Mutants: 2", "KILLED       1", "SURVIVED     1")
    }

    @OnGradleVersions
    fun `a project's own vintage engine is used instead of the supplied one`(version: String) {
        assertSummaryLines(run(version, ownEngine = true), "Mutants: 2", "KILLED       1", "SURVIVED     1")
    }

    private fun run(
        version: String,
        ownEngine: Boolean,
    ): String {
        val project = TestKitProject(dir, version)
        project.setUp(
            sources = mapOf("demo/Gate.java" to GATE),
            tests = mapOf("demo/GateTest.java" to JUNIT4_GATE_TEST),
        )
        val engine = if (ownEngine) "testRuntimeOnly files(${project.jars(VINTAGE_JAR)})" else ""
        project.write(
            "build.gradle",
            """
            plugins {
                id 'java'
                id 'io.github.rodrigotimoteo.mutkt'
            }
            dependencies {
                testImplementation files(${project.jars(JUNIT4_JARS)})
                $engine
            }
            tasks.named('test') { useJUnit() }
            mutkt {
                engines = ['junit-vintage']
                operators = ['conditionals-boundary', 'negate-conditionals']
            }
            """.trimIndent(),
        )
        val result = project.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        return project.report.readText()
    }

    private companion object {
        val VINTAGE_JAR = Regex("^junit-vintage-engine")
    }
}
