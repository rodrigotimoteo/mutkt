package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.GATE
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** failOnNewSurvivors and mutktUpdateBaseline on a real build with the configuration cache on. */
@Tag("e2e")
@Tag("matrix")
@Timeout(240)
class MutKtSurvivorBaselineTest {
    @TempDir
    lateinit var dir: File

    private val boundary = "conditionals-boundary:demo.Gate#isAdult(I)Z:0"

    @OnGradleVersions
    fun `only survivors missing from the committed baseline fail the build`(version: String) {
        val project = TestKitProject(dir, version)
        val gate = "failOnNewSurvivors = true"
        project.setUp("mutkt { $gate; failBelowScore = 100 }", mapOf("demo/Gate.java" to GATE), GATE_TESTS)

        val unknown = project.buildAndFail()
        assertTrue("is below the threshold of 100.00%" in unknown.output, unknown.output)
        assertTrue("1 survivor is not in the MutKt survivor baseline" in unknown.output, unknown.output)
        assertTrue("  $boundary\n" in unknown.output, unknown.output)
        assertTrue(project.report.isFile, unknown.output)

        // The update accepts survivors, so failBelowScore does not stop it.
        val update = project.build("mutktUpdateBaseline")
        assertEquals(TaskOutcome.SUCCESS, update.task(":mutktUpdateBaseline")?.outcome, update.output)
        assertTrue(boundary in dir.resolve("mutkt-baseline.json").readText(), update.output)
        project.setUp("mutkt { $gate }", mapOf("demo/Gate.java" to GATE), GATE_TESTS)
        assertEquals(TaskOutcome.SUCCESS, project.build().task(":mutkt")?.outcome)

        // A new boundary earlier in the method takes ordinal 0, which the baseline accepted for the old one.
        project.write(
            "src/main/java/demo/Gate.java",
            GATE.replace("return age", "if (age > 150) return false; return age"),
        )
        val moved = project.buildAndFail()
        assertTrue("  $boundary (accepted before its method changed: review it)\n" in moved.output, moved.output)
    }

    private companion object {
        // 30 and 10 miss the boundary at 18 (and 150): conditionals-boundary survives, negate-conditionals is killed.
        val GATE_TESTS =
            mapOf(
                "demo/GateTest.java" to
                    """
                    package demo;
                    import org.junit.jupiter.api.Assertions;
                    import org.junit.jupiter.api.Test;
                    class GateTest {
                        @Test void adult() { Assertions.assertTrue(new Gate().isAdult(30)); }
                        @Test void child() { Assertions.assertFalse(new Gate().isAdult(10)); }
                    }
                    """.trimIndent(),
            )
    }
}
