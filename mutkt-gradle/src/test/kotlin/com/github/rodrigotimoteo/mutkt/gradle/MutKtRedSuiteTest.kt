package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** A failing test unrelated to the mutants must not count as a kill for every one of them. */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtRedSuiteTest {
    @TempDir
    lateinit var dir: File

    private val sources = mapOf("demo/Calc.java" to CALC)
    private val tests = mapOf("demo/CalcTest.java" to RED_TEST)

    @OnGradleVersions
    fun `a red suite fails the task naming the first failing test and publishes no score`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp("mutkt { failBelowScore = 100 }", sources, tests)

        val result = project.buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":mutkt")?.outcome, result.output)
        assertTrue("demo.CalcTest.unrelated" in result.output, result.output)
        assertTrue("no mutant was scored" in result.output, result.output)
        assertFalse("Score:" in result.output, result.output)
        assertFalse(project.report.exists(), "no report may be written for a red suite")
    }

    @OnGradleVersions
    fun `the same assertion excluded by tag leaves a green suite with a kill and a survivor`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp("mutkt { excludeTags.add('flaky') }", sources, tests)

        val result = project.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        val summary = project.report.readText()
        assertTrue(summary.lines().any { it.trim() == "KILLED       3" }, summary)
        assertTrue(summary.lines().any { it.trim() == "SURVIVED     1" }, summary)
    }

    private companion object {
        val CALC =
            """
            package demo;
            public class Calc {
                public int add(int a, int b) { return a + b; }
                public boolean isPositive(int n) { return n > 0; }
            }
            """.trimIndent()

        // isPositive is asserted only far from the boundary, so its boundary mutant survives.
        val RED_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void adds() { Assertions.assertEquals(5, new Calc().add(2, 3)); }
                @Test void positive() { Assertions.assertTrue(new Calc().isPositive(5)); }
                @Tag("flaky") @Test void unrelated() { Assertions.fail("environment problem"); }
            }
            """.trimIndent()
    }
}
