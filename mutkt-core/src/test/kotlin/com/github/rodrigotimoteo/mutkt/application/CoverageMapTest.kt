package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 30.seconds
private val SUITE = 9.seconds

class CoverageMapTest {
    private val add = mutant(method = "add")
    private val sub = mutant(method = "sub")
    private val init = mutant(method = "<clinit>")
    private val unmapped = mutant(method = "unmapped")
    private val probes = mapOf(add.id to Probe("a.Foo", 0), sub.id to Probe("a.Foo", 1), init.id to Probe("a.Foo", 2))

    private fun covered(
        vararg tests: Pair<String, Duration>,
        hits: Set<Int> = setOf(0),
        global: Set<Int> = emptySet(),
    ) = Coverage(
        tests.map { (name, elapsed) -> TestCoverage(TestId(name), hits.map { Probe("a.Foo", it) }.toSet(), elapsed) },
        global.map { Probe("a.Foo", it) }.toSet(),
    )

    private fun map(coverage: Coverage?) = CoverageMap(probes, coverage, TIMEOUT, SUITE)

    @Test
    fun `a mutant no test class reaches selects no test`() =
        assertEquals(emptyList<TestId>(), map(covered("ATest" to 1.seconds)).selection(sub).tests)

    @Test
    fun `the test classes that reach a mutant run in baseline order, whatever their times`() {
        val coverage = covered("ATest" to 3.seconds, "BTest" to 1.seconds, "CTest" to 2.seconds, "DTest" to 1.seconds)
        val tests = map(coverage).selection(add).tests
        assertEquals(listOf("ATest", "BTest", "CTest", "DTest").map(::TestId), tests)
    }

    @Test
    fun `only the test classes that reach the mutant's probe are selected`() {
        val coverage =
            Coverage(
                listOf(
                    TestCoverage(TestId("ATest"), setOf(Probe("a.Foo", 1)), 1.seconds),
                    TestCoverage(TestId("BTest"), setOf(Probe("a.Foo", 0)), 1.seconds),
                    TestCoverage(TestId("CTest"), setOf(Probe("a.Bar", 0)), 1.seconds),
                ),
                emptySet(),
            )
        assertEquals(listOf(TestId("BTest")), map(coverage).selection(add).tests)
    }

    @Test
    fun `the deadline is four times the covering classes' time plus the longest class as warm-up, plus four seconds`() {
        val coverage = covered("ATest" to 1.seconds, "BTest" to 1500.milliseconds, "CTest" to 250.milliseconds)
        assertEquals(21.seconds, map(coverage).selection(add).deadline)
    }

    @Test
    fun `the warm-up is the longest test class's time even when it does not reach the mutant`() {
        val coverage =
            Coverage(
                listOf(
                    TestCoverage(TestId("ATest"), emptySet(), 2.seconds),
                    TestCoverage(TestId("BTest"), setOf(Probe("a.Foo", 0)), 1.seconds),
                    TestCoverage(TestId("CTest"), emptySet(), 3.seconds),
                ),
                emptySet(),
            )
        assertEquals(Selection(listOf(TestId("BTest")), 20.seconds), map(coverage).selection(add))
    }

    @Test
    fun `the timeout caps the deadline of covering classes`() {
        val coverage = covered("ATest" to 6.seconds, "BTest" to 1.seconds)
        assertEquals(TIMEOUT, map(coverage).selection(add).deadline)
    }

    @Test
    fun `a deadline that exactly fits the timeout is kept`() {
        assertEquals(TIMEOUT, map(covered("ATest" to 3250.milliseconds)).selection(add).deadline)
        assertEquals(29.seconds, map(covered("ATest" to 3125.milliseconds)).selection(add).deadline)
    }

    @Test
    fun `a mutant without a probe runs the whole suite`() =
        assertEquals(Selection(null, SUITE), map(covered("ATest" to 1.seconds)).selection(unmapped))

    @Test
    fun `a mutant whose probe was hit outside any test class runs the whole suite`() {
        val coverage = covered("ATest" to 1.seconds, global = setOf(0))
        assertEquals(Selection(null, SUITE), map(coverage).selection(add))
        assertEquals(Selection(null, SUITE), map(covered(global = setOf(1))).selection(sub))
    }

    @Test
    fun `a mutant in a class initialiser runs the whole suite even when a test class reached it`() =
        assertEquals(Selection(null, SUITE), map(covered("ATest" to 1.seconds, hits = setOf(2))).selection(init))

    @Test
    fun `without coverage every mutant runs the whole suite`() =
        listOf(add, sub, init, unmapped).forEach { assertEquals(Selection(null, SUITE), map(null).selection(it)) }
}
