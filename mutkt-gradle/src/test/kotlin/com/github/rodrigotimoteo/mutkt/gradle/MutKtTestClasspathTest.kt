package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Resources that only the `test` task has on its classpath must reach the mutants' test runs too. */
@Tag("e2e")
@Tag("matrix")
@Timeout(180)
class MutKtTestClasspathTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `a resource added only to the test task classpath is seen by the mutant runs and invalidates mutkt`(
        version: String,
    ) {
        val project = TestKitProject(dir, version)
        project.setUp(
            // Prepended on purpose: the test task's order, not the source set's, decides which order.txt wins.
            "tasks.test { classpath = files('extra') + classpath }",
            sources = mapOf("demo/Calc.java" to CALC),
            tests = mapOf("demo/CalcTest.java" to TEST),
        )
        project.write("src/test/resources/order.txt", "source set")
        project.write("extra/order.txt", "test task")
        project.write("extra/marker.txt", "one")

        val plain = project.build("test")
        assertEquals(TaskOutcome.SUCCESS, plain.task(":test")?.outcome, plain.output)
        assertTrue(project.ranTests("demo.CalcTest"), plain.output)

        val first = project.build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":mutkt")?.outcome, first.output)
        assertEquals(null, first.task(":test"), "mutkt must not run the test task\n${first.output}")
        val summary = project.report.readText()
        // add() is killed by its assertion (the mutants do replace the originals); isPositive() is unasserted.
        assertTrue(summary.lines().any { it.trim() == "KILLED       2" }, summary)
        assertTrue(summary.lines().any { it.trim() == "SURVIVED     2" }, summary)

        assertEquals(TaskOutcome.UP_TO_DATE, project.build().task(":mutkt")?.outcome)

        project.write("extra/marker.txt", "two")
        assertEquals(TaskOutcome.SUCCESS, project.build().task(":mutkt")?.outcome)
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

        val TEST =
            """
            package demo;
            import java.nio.charset.StandardCharsets;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void resourcesComeFromTheTestTask() throws Exception {
                    Assertions.assertNotNull(getClass().getResource("/marker.txt"));
                    byte[] order = getClass().getResourceAsStream("/order.txt").readAllBytes();
                    Assertions.assertEquals("test task", new String(order, StandardCharsets.UTF_8));
                }
                @Test void adds() { Assertions.assertEquals(5, new Calc().add(2, 3)); }
                @Test void callsWithoutAsserting() { new Calc().isPositive(5); }
            }
            """.trimIndent()
    }
}
