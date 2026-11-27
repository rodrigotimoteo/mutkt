package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import java.nio.file.Files
import java.nio.file.Path

private const val CALC = """package demo

class Calc {
    fun add(a: Int, b: Int): Int = a + b

    fun max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }
}
"""

/** Writes the demo source under [root] and returns a report that uses every status once. */
internal fun fixtureReport(root: Path): MutationReport {
    Files.createDirectories(root.resolve("demo"))
    Files.writeString(root.resolve("demo/Calc.kt"), CALC)
    return MutationReport(
        listOf(
            result(MutantStatus.KILLED, "arithmetic", "add", 4, "Replaced + with -", killedBy = "demo.CalcTest.adds()")
                .copy(coveredBy = listOf("demo.CalcTest")),
            result(MutantStatus.SURVIVED, "negate-conditionals", "max", 7, "Negated conditional")
                .copy(coveredBy = listOf("demo.MaxTest", "demo.CalcTest")),
            result(MutantStatus.TIMEOUT, "arithmetic", "add", 4, "Replaced + with *", ordinal = 1),
            result(MutantStatus.NO_COVERAGE, "return-values", "max", 7, "Replaced return value", ordinal = 1)
                .copy(coveredBy = emptyList()),
            result(
                MutantStatus.ERROR,
                "arithmetic",
                "add",
                4,
                "Replaced + with /",
                ordinal = 2,
                detail = "VerifyError: bad",
            ),
            result(
                MutantStatus.SURVIVED,
                "arithmetic",
                "run",
                null,
                "No debug info",
                className = "demo.Other",
                sourceFile = null,
            ),
            result(MutantStatus.SURVIVED, "arithmetic", "gone", 99, "Line past the end of the source"),
        ),
        omitted = 2,
    )
}

@Suppress("LongParameterList")
private fun result(
    status: MutantStatus,
    operator: String,
    method: String,
    line: Int?,
    description: String,
    className: String = "demo.Calc",
    sourceFile: String? = "Calc.kt",
    ordinal: Int = 0,
    detail: String? = null,
    killedBy: String? = null,
): MutantResult {
    val op = OperatorId(operator)
    val id = MutantId(op, className, method, "(II)I", ordinal)
    return MutantResult(
        Mutant(id, op, className, method, "(II)I", line, description, sourceFile),
        status,
        detail,
        killedBy,
    )
}
