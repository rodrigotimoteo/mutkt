package com.github.rodrigotimoteo.mutkt.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MutationReportTest {
    private fun result(
        status: MutantStatus,
        className: String = "a.Foo",
        ordinal: Int = 0,
    ): MutantResult {
        val operator = OperatorId("negate")
        val id = MutantId(operator, className, "run", "()V", ordinal)
        return MutantResult(Mutant(id, operator, className, "run", "()V", null, "negated"), status)
    }

    private fun report(vararg statuses: MutantStatus) =
        MutationReport(statuses.mapIndexed { i, s -> result(s, ordinal = i) })

    @Test
    fun `omitted defaults to zero and does not change the score`() {
        assertEquals(0, report(MutantStatus.KILLED).omitted)
        assertEquals(1.0, MutationReport(listOf(result(MutantStatus.KILLED)), omitted = 2).score)
    }

    @Test
    fun `empty report has no score`() = assertNull(MutationReport(emptyList()).score)

    @Test
    fun `report of only errors has no score`() = assertNull(report(MutantStatus.ERROR, MutantStatus.ERROR).score)

    @Test
    fun `killed counts as detected`() = assertEquals(1.0, report(MutantStatus.KILLED).score)

    @Test
    fun `timeout counts as detected`() = assertEquals(1.0, report(MutantStatus.TIMEOUT).score)

    @Test
    fun `survived and no coverage count against`() {
        assertEquals(0.0, report(MutantStatus.SURVIVED).score)
        assertEquals(0.0, report(MutantStatus.NO_COVERAGE).score)
    }

    @Test
    fun `errors are excluded from the denominator`() =
        assertEquals(0.5, report(MutantStatus.KILLED, MutantStatus.SURVIVED, MutantStatus.ERROR).score)

    @Test
    fun `mixed statuses`() {
        val statuses =
            listOf(MutantStatus.KILLED, MutantStatus.TIMEOUT, MutantStatus.SURVIVED, MutantStatus.NO_COVERAGE)
        assertEquals(0.5, report(*statuses.toTypedArray()).score)
    }

    @Test
    fun `byClass splits results and scores each class`() {
        val report =
            MutationReport(
                listOf(
                    result(MutantStatus.KILLED, "a.Foo", 0),
                    result(MutantStatus.SURVIVED, "a.Foo", 1),
                    result(MutantStatus.SURVIVED, "a.Bar", 0),
                ),
            )
        assertEquals(setOf("a.Foo", "a.Bar"), report.byClass.keys)
        assertEquals(0.5, report.byClass.getValue("a.Foo").score)
        assertEquals(0.0, report.byClass.getValue("a.Bar").score)
    }

    @Test
    fun `byClass of an empty report is empty`() =
        assertEquals(emptyMap<String, MutationReport>(), MutationReport(emptyList()).byClass)

    @Test
    fun `status set is exactly the five decided`() =
        assertEquals(
            listOf("KILLED", "SURVIVED", "TIMEOUT", "NO_COVERAGE", "ERROR"),
            MutantStatus.entries.map { it.name },
        )
}
