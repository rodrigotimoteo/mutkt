package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
import com.github.rodrigotimoteo.mutkt.domain.KillKind.ASSERTION
import com.github.rodrigotimoteo.mutkt.domain.KillKind.EXCEPTION
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.ERROR
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.KILLED
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.NO_COVERAGE
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.SURVIVED
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.TIMEOUT
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.FakeSourceLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConsoleReportWriterTest {
    @Suppress("LongParameterList")
    private fun result(
        status: MutantStatus,
        className: String = "com.acme.Foo",
        method: String = "bar",
        line: Int? = 12,
        descriptor: String = "()V",
        operator: String = "negate-conditionals",
        coveredBy: List<String>? = null,
    ): MutantResult {
        val op = OperatorId(operator)
        val id = MutantId(op, className, method, descriptor, 0)
        return MutantResult(
            Mutant(id, op, className, method, descriptor, line, "negated if"),
            status,
            coveredBy = coveredBy,
        )
    }

    private fun render(
        report: MutationReport,
        lines: Map<Pair<String, Int>, String> = emptyMap(),
    ): String = StringBuilder().also { ConsoleReportWriter(it, FakeSourceLookup(lines)).write(report) }.toString()

    @Test
    fun `empty report has zero counts and no score`() {
        assertEquals(
            """
            Mutation report
            Mutants: 0
              KILLED       0
              SURVIVED     0
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        0
            Score: n/a

            """.trimIndent(),
            render(MutationReport(emptyList())),
        )
    }

    @Test
    fun `a line before the score splits assertion kills from exception kills`() {
        val kills = listOf(ASSERTION, ASSERTION, EXCEPTION).map { result(KILLED).copy(killedBy = "t", killKind = it) }
        val text = render(MutationReport(kills + result(SURVIVED)))
        assertTrue("  KILLED       3\n" in text, text)
        assertTrue("Killed 3 (assertions 2, exceptions 1)\nScore: 75.0%\n" in text, text)
        assertTrue("assertions" !in render(MutationReport(listOf(result(SURVIVED)))))
    }

    @Test
    fun `score line names the mutants the cap omitted`() {
        val text = render(MutationReport(listOf(result(KILLED)), omitted = 2))
        assertTrue("Score: 100.0% (1 evaluated, 2 omitted by the cap)\n" in text, text)
        assertTrue("omitted" !in render(MutationReport(listOf(result(KILLED)))))
    }

    @Test
    fun `the mutant count names the results reused from history`() {
        val text = render(MutationReport(listOf(result(KILLED).copy(reused = true), result(SURVIVED, method = "baz"))))
        assertTrue("Mutants: 2 (1 reused)\n" in text, text)
        assertTrue("reused" !in render(MutationReport(listOf(result(KILLED)))))
    }

    @Test
    fun `a capped deadline adds a warning naming the timeoutMs to set`() {
        val cap = DeadlineCap(10.seconds, 12_345.milliseconds, 53_380.milliseconds)
        val text = render(MutationReport(listOf(result(KILLED)), deadlineCap = cap))
        val warning =
            "Per-mutant deadline capped at timeoutMs (10.0s); the tests took 12.3s, " +
                "so slow mutants may end in TIMEOUT: set timeoutMs to at least 54000"
        assertTrue("Score: 100.0%\n$warning\n" in text, text)
        assertTrue("deadline" !in render(MutationReport(listOf(result(KILLED)))))
    }

    @Test
    fun `only errors give score n-a and a class row without score`() {
        assertEquals(
            """
            Mutation report
            Mutants: 1
              KILLED       0
              SURVIVED     0
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        1
            Score: n/a

            Class         Detected  Score
            com.acme.Foo       0/0    n/a

            Errors (1)
            negate-conditionals:com.acme.Foo#bar()V:0

            """.trimIndent(),
            render(MutationReport(listOf(result(ERROR)))),
        )
    }

    @Test
    fun `mixed report lists classes sorted and survivors without source`() {
        val report =
            MutationReport(
                listOf(
                    result(KILLED, "com.acme.Zed"),
                    result(KILLED),
                    result(TIMEOUT),
                    result(SURVIVED),
                    result(NO_COVERAGE),
                ),
            )
        assertEquals(
            """
            Mutation report
            Mutants: 5
              KILLED       2
              SURVIVED     1
              TIMEOUT      1
              NO_COVERAGE  1
              ERROR        0
            Score: 60.0%

            Class         Detected   Score
            com.acme.Foo       2/4   50.0%
            com.acme.Zed       1/1  100.0%

            Survivors (1 in 1 method)
            com.acme.Foo.bar()V  1 survivor, covered by the whole suite
              line 12  negate-conditionals  negated if

            Hints
              negate-conditionals: no test checks what happens on both sides of the condition

            """.trimIndent(),
            render(report),
        )
    }

    @Test
    fun `survivor shows available neighbouring lines and marks the mutated one`() {
        val lines =
            mapOf(
                ("com.acme.Foo" to 11) to "val a = 1",
                ("com.acme.Foo" to 12) to "if (a == 1) {",
            )
        assertEquals(
            """
            Mutation report
            Mutants: 1
              KILLED       0
              SURVIVED     1
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        0
            Score: 0.0%

            Class         Detected  Score
            com.acme.Foo       0/1   0.0%

            Survivors (1 in 1 method)
            com.acme.Foo.bar()V  1 survivor, covered by the whole suite
              line 12  negate-conditionals  negated if
                    11 | val a = 1
                >   12 | if (a == 1) {

            Hints
              negate-conditionals: no test checks what happens on both sides of the condition

            """.trimIndent(),
            render(MutationReport(listOf(result(SURVIVED))), lines),
        )
    }

    @Test
    fun `class column excludes errors like the score does`() {
        val report =
            MutationReport(
                listOf(result(KILLED), result(ERROR), result(SURVIVED, line = null), result(ERROR, "com.acme.Err")),
            )
        assertEquals(
            """
            Mutation report
            Mutants: 4
              KILLED       1
              SURVIVED     1
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        2
            Score: 50.0%

            Class         Detected  Score
            com.acme.Err       0/0    n/a
            com.acme.Foo       1/2  50.0%

            Survivors (1 in 1 method)
            com.acme.Foo.bar()V  1 survivor, covered by the whole suite
              no line  negate-conditionals  negated if

            Hints
              negate-conditionals: no test checks what happens on both sides of the condition

            Errors (2)
            negate-conditionals:com.acme.Foo#bar()V:0
            negate-conditionals:com.acme.Err#bar()V:0

            """.trimIndent(),
            render(report),
        )
    }

    @Test
    fun `errors list the mutant id with its cause, in discovery order`() {
        val report =
            MutationReport(
                listOf(
                    result(KILLED),
                    result(ERROR, method = "two").copy(detail = "java.lang.VerifyError: bad"),
                    result(ERROR, method = "one"),
                ),
            )
        assertEquals(
            """
            Mutation report
            Mutants: 3
              KILLED       1
              SURVIVED     0
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        2
            Score: 100.0%

            Class         Detected   Score
            com.acme.Foo       1/1  100.0%

            Errors (2)
            negate-conditionals:com.acme.Foo#two()V:0: java.lang.VerifyError: bad
            negate-conditionals:com.acme.Foo#one()V:0

            """.trimIndent(),
            render(report),
        )
    }

    @Test
    fun `only the first ten errors are listed`() {
        val errors = (0 until 12).map { result(ERROR, method = "m$it").copy(detail = "cause $it") }
        val text = render(MutationReport(errors))
        assertEquals(10, text.lines().count { it.startsWith("negate-conditionals:") })
        assertTrue("Errors (12)\n" in text, text)
        assertTrue(text.endsWith("... and 2 more\n"), text)
    }

    @Test
    fun `survivors are grouped by method, most first, with covering tests and one hint per operator`() {
        val report =
            MutationReport(
                listOf(
                    result(SURVIVED, line = null, method = "n"),
                    result(
                        SURVIVED,
                        line = 31,
                        method = "b",
                        descriptor = "(I)V",
                        coveredBy = listOf("ATest", "CTest"),
                    ),
                    result(SURVIVED, line = 5, method = "a", coveredBy = listOf("CalcTest")),
                    result(SURVIVED, line = 30, method = "b", descriptor = "(I)V", operator = "conditionals-boundary")
                        .copy(coveredBy = listOf("BTest", "ATest")),
                ),
            )
        assertEquals(
            """
            Survivors (4 in 3 methods)
            com.acme.Foo.b(I)V  2 survivors, covered by ATest, CTest, BTest
              line 30  conditionals-boundary  negated if
              line 31  negate-conditionals  negated if
            com.acme.Foo.a()V  1 survivor, covered by CalcTest
              line 5  negate-conditionals  negated if
            com.acme.Foo.n()V  1 survivor, covered by the whole suite
              no line  negate-conditionals  negated if

            Hints
              conditionals-boundary: no test checks the value exactly at the boundary
              negate-conditionals: no test checks what happens on both sides of the condition

            """.trimIndent(),
            "Survivors (" + render(report).substringAfter("Survivors ("),
        )
    }

    @Test
    fun `only the first ten methods are listed, with hints for their operators only`() {
        val worst = listOf(result(SURVIVED, method = "z", line = 1), result(SURVIVED, method = "z", line = 2))
        val others =
            ('a'..'l').map {
                result(
                    SURVIVED,
                    method = it.toString(),
                    operator =
                        if (it <
                            'j'
                        ) {
                            "negate-conditionals"
                        } else {
                            "arithmetic"
                        },
                )
            }
        val text = render(MutationReport(worst + others))
        assertTrue("Survivors (14 in 13 methods)\n" in text, text)
        assertEquals(10, text.lines().count { it.startsWith("com.acme.Foo.") }, text)
        assertTrue("com.acme.Foo.i()V" in text && "com.acme.Foo.j()V" !in text, text)
        assertTrue("... and 3 more methods (3 survivors) in the HTML report\n" in text, text)
        assertTrue("arithmetic" !in text, text)
    }

    @Test
    fun `only the first five survivors of a method are listed`() {
        val text = render(MutationReport((1..7).map { result(SURVIVED, line = it) }))
        assertTrue("com.acme.Foo.bar()V  7 survivors, covered by the whole suite\n" in text, text)
        assertEquals(5, text.lines().count { it.startsWith("  line ") }, text)
        assertTrue("  line 5  negate-conditionals  negated if\n  ... and 2 more\n" in text, text)
    }

    @Test
    fun `a method with no covering test class named has no covered-by clause`() {
        val text = render(MutationReport(listOf(result(SURVIVED, coveredBy = emptyList()))))
        assertTrue("com.acme.Foo.bar()V  1 survivor\n" in text, text)
    }

    @Test
    fun `at most three covering test classes are named`() {
        val text = render(MutationReport(listOf(result(SURVIVED, coveredBy = listOf("A", "B", "C", "D", "E")))))
        assertTrue("com.acme.Foo.bar()V  1 survivor, covered by A, B, C and 2 more\n" in text, text)
    }
}
