package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
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
    private fun result(
        status: MutantStatus,
        className: String = "com.acme.Foo",
        method: String = "bar",
        line: Int? = 12,
        descriptor: String = "()V",
    ): MutantResult {
        val op = OperatorId("negate-conditionals")
        val id = MutantId(op, className, method, descriptor, 0)
        return MutantResult(Mutant(id, op, className, method, descriptor, line, "negated if"), status)
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
    fun `score line names the mutants the cap omitted`() {
        val text = render(MutationReport(listOf(result(KILLED)), omitted = 2))
        assertTrue("Score: 100.0% (1 evaluated, 2 omitted by the cap)\n" in text, text)
        assertTrue("omitted" !in render(MutationReport(listOf(result(KILLED)))))
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

            Survivors (1)
            com.acme.Foo.bar()V (com.acme.Foo:12)  negate-conditionals  negated if

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

            Survivors (1)
            com.acme.Foo.bar()V (com.acme.Foo:12)  negate-conditionals  negated if
                  11 | val a = 1
              >   12 | if (a == 1) {

            """.trimIndent(),
            render(MutationReport(listOf(result(SURVIVED))), lines),
        )
    }

    @Test
    fun `class column excludes errors like the score does`() {
        val report =
            MutationReport(
                listOf(
                    result(KILLED),
                    result(ERROR),
                    result(SURVIVED, line = null),
                    result(ERROR, "com.acme.Err"),
                ),
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

            Survivors (1)
            com.acme.Foo.bar()V (com.acme.Foo)  negate-conditionals  negated if

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
    fun `survivors are ordered by class then line and overloads differ`() {
        val report =
            MutationReport(
                listOf(
                    result(SURVIVED, line = null, method = "n"),
                    result(SURVIVED, line = 30, method = "b", descriptor = "(I)V"),
                    result(SURVIVED, line = 30, method = "b"),
                    result(SURVIVED, line = 5, method = "a"),
                ),
            )
        assertEquals(
            """
            Mutation report
            Mutants: 4
              KILLED       0
              SURVIVED     4
              TIMEOUT      0
              NO_COVERAGE  0
              ERROR        0
            Score: 0.0%

            Class         Detected  Score
            com.acme.Foo       0/4   0.0%

            Survivors (4)
            com.acme.Foo.a()V (com.acme.Foo:5)  negate-conditionals  negated if
            com.acme.Foo.b()V (com.acme.Foo:30)  negate-conditionals  negated if
            com.acme.Foo.b(I)V (com.acme.Foo:30)  negate-conditionals  negated if
            com.acme.Foo.n()V (com.acme.Foo)  negate-conditionals  negated if

            """.trimIndent(),
            render(report),
        )
    }
}
