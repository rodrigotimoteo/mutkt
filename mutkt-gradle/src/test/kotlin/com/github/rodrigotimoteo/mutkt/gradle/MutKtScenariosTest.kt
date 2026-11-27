package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * One real Gradle build per scenario over a tiny Java project; each runs on every Gradle version of the matrix.
 * The report summary lines (`Mutants: n`, `KILLED       n`) are the observable result.
 */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtScenariosTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `a weak test lets a boundary mutant survive while the others are killed`(version: String) {
        val summary = run(version, "", mapOf("demo/Calc.java" to CALC), mapOf("demo/CalcTest.java" to WEAK_TEST))

        assertLine(summary, "Mutants: 4")
        assertLine(summary, "KILLED       3")
        assertLine(summary, "SURVIVED     1")
    }

    @OnGradleVersions
    fun `without selected operators only the six defaults run`(version: String) {
        // The word "operators" in the DSL turns off the fixture's default pin; no operator is selected.
        val summary =
            run(
                version,
                "// operators: none selected",
                mapOf("demo/Calc.java" to CALC),
                mapOf(
                    "demo/CalcTest.java" to WEAK_TEST,
                ),
            )

        // Two sites of add and two of isPositive; true-returns and false-returns would add two more on isPositive.
        assertLine(summary, "Mutants: 4")
        assertTrue("true-returns" !in summary && "false-returns" !in summary, summary)
    }

    @OnGradleVersions
    fun `excluded classes and methods are not mutated`(version: String) {
        val sources = mapOf("demo/Calc.java" to CALC, "demo/Other.java" to OTHER)
        val dsl = "mutkt { excludedClasses.add('demo.Other'); excludedMethods.add('isPositive') }"

        val summary = run(version, dsl, sources, mapOf("demo/CalcTest.java" to WEAK_TEST))

        assertLine(summary, "Mutants: 2")
        assertLine(summary, "Score: 100.0%")
    }

    @OnGradleVersions
    fun `a suppressed method is not mutated`(version: String) {
        val sources =
            mapOf(
                "demo/Calc.java" to CALC.replace("    public boolean", "    @SuppressMutations\n    public boolean"),
            )
        val imported = sources.mapValues { it.value.replace("package demo;", "package demo;\nimport $SUPPRESS;") }

        val summary = run(version, "", imported, mapOf("demo/CalcTest.java" to WEAK_TEST))

        assertLine(summary, "Mutants: 2")
        assertLine(summary, "Score: 100.0%")
    }

    @OnGradleVersions
    fun `excluding the tag of the only asserting test leaves its mutants undetected`(version: String) {
        val sources = mapOf("demo/Calc.java" to CALC)
        val tests = mapOf("demo/CalcTest.java" to TAGGED_TEST)

        val all = run(version, "", sources, tests)
        val filtered = run(version, "mutkt { excludeTags.add('slow') }", sources, tests)

        assertLine(all, "KILLED       4")
        // add still runs in the untagged test; isPositive is reached by no test left.
        assertLine(filtered, "SURVIVED     2")
        assertLine(filtered, "NO_COVERAGE  2")
    }

    @OnGradleVersions
    fun `a mutant that never ends times out and the build still finishes`(version: String) {
        val project = TestKitProject(dir, version)
        // A derived deadline is at least 4 s, so 2 s always caps it.
        project.setUp(
            "mutkt { timeoutMs = 2000L }",
            mapOf("demo/Loop.java" to LOOP),
            mapOf("demo/LoopTest.java" to LOOP_TEST),
        )
        val output = project.build().output
        val summary = project.report.readText()

        assertLine(summary, "TIMEOUT      1")
        val capped = "Per-mutant deadline capped at timeoutMs (2.0s); the tests took "
        assertTrue(capped in summary, summary)
        // Warned right after the baseline, then repeated in the summary logged at the end.
        assertEquals(2, output.split(capped).size - 1, output)
        assertTrue(output.indexOf(capped) < output.indexOf("Mutation report"), output)
    }

    private fun assertLine(
        summary: String,
        line: String,
    ) = assertTrue(summary.lines().any { it.trim() == line.trim() }, "Missing line '$line' in:\n$summary")

    private fun run(
        version: String,
        dsl: String,
        sources: Map<String, String>,
        tests: Map<String, String>,
    ): String {
        val project = TestKitProject(dir, version)
        project.setUp(dsl, sources, tests)
        val result = project.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        return project.report.readText()
    }

    private companion object {
        const val SUPPRESS = "com.github.rodrigotimoteo.mutkt.annotations.SuppressMutations"

        val CALC =
            """
            package demo;
            public class Calc {
                public int add(int a, int b) { return a + b; }
                public boolean isPositive(int n) { return n > 0; }
            }
            """.trimIndent()

        val OTHER =
            """
            package demo;
            public class Other {
                public int twice(int a) { return a * 2; }
            }
            """.trimIndent()

        val WEAK_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void adds() { Assertions.assertEquals(5, new Calc().add(2, 3)); }
                @Test void positive() { Assertions.assertTrue(new Calc().isPositive(5)); }
            }
            """.trimIndent()

        val TAGGED_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void runs() { new Calc().add(2, 3); }
                @Tag("slow") @Test void asserts() {
                    Assertions.assertEquals(5, new Calc().add(2, 3));
                    Assertions.assertTrue(new Calc().isPositive(1));
                    Assertions.assertFalse(new Calc().isPositive(0));
                }
            }
            """.trimIndent()

        // `i += step` mutated to `i -= step` never reaches n; with long the counter cannot wrap around to end the loop.
        val LOOP =
            """
            package demo;
            public class Loop {
                public long sum(long n, long step) {
                    long total = 0;
                    for (long i = 0; i < n; i += step) { total += i; }
                    return total;
                }
            }
            """.trimIndent()

        val LOOP_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class LoopTest {
                @Test void sums() { Assertions.assertEquals(6L, new Loop().sum(4, 1)); }
            }
            """.trimIndent()
    }
}
