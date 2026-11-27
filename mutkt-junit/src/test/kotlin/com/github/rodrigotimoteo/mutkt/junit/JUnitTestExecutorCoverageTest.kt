package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JUnitTestExecutorCoverageTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private fun baseline(
        fixture: CoverageFixture,
        excludeTags: List<String> = emptyList(),
    ) = JUnitTestExecutor(fixture.project, fixture.tests, runtimeJars(), excludeTags = excludeTags)
        .also { executors += it }
        .baseline(deadline, fixture.instrumented)

    private fun Coverage.hits() = tests.map { it.test to it.hits }

    @Test
    fun `each test class owns the probes it reached`() {
        val fixture =
            coverageFixture(
                "class ATest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }\n" +
                    "class BTest { @Test fun subtracts() { assertEquals(1, Calc().sub(3, 2)) } }\n" +
                    "class CTest { @Test fun nothing() {} }",
            )
        val baseline = baseline(fixture)
        assertEquals(Outcome.Survived, baseline.outcome)
        val coverage = baseline.coverage!!
        val expected = listOf(TestId("ATest") to setOf(ADD), TestId("BTest") to setOf(SUB), TestId("CTest") to setOf())
        assertEquals(expected, coverage.hits())
        assertEquals(emptySet<Probe>(), coverage.global)
    }

    @Test
    fun `a nested test class's probes belong to its enclosing root class`() {
        val test = "class OuterTest { @Nested inner class Inner { @Test fun adds() { Calc().add(2, 3) } } }"
        assertEquals(listOf(TestId("OuterTest") to setOf(ADD)), baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `disabled and tag-filtered test classes are absent`() {
        val tests =
            "class ATest { @Test fun adds() { Calc().add(2, 3) } }\n" +
                "@Disabled class DTest { @Test fun subtracts() { Calc().sub(3, 2) } }\n" +
                "@Tag(\"slow\") class STest { @Test fun subtracts() { Calc().sub(3, 2) } }"
        val coverage = baseline(coverageFixture(tests), excludeTags = listOf("slow")).coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), coverage.hits())
        assertEquals(emptySet<Probe>(), coverage.global)
    }

    @Test
    fun `a probe reached while discovering the tests is global`() {
        val generator =
            "class Names : DisplayNameGenerator.Standard() { " +
                "override fun generateDisplayNameForClass(type: Class<*>) = \"n\" + Calc().sub(3, 2) }\n"
        val test = "@DisplayNameGeneration(Names::class) class ATest { @Test fun adds() { Calc().add(2, 3) } }"
        val coverage = baseline(coverageFixture(generator + test)).coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), coverage.hits())
        assertEquals(setOf(SUB), coverage.global)
    }

    @Test
    fun `reading probes never initializes a class a test only loaded`() {
        val extra =
            "object Log { val events = mutableListOf<String>() }\n" +
                "class Boom { companion object { @JvmField var probes: BooleanArray? = null\n" +
                "init { Log.events += \"boom\" } } }"
        val tests =
            "class ATest { @Test fun loads() { assertEquals(\"Boom\", Boom::class.java.name) } }\n" +
                "class BTest { @Test fun untouched() { assertEquals(listOf<String>(), Log.events) } }"
        val baseline = baseline(coverageFixture(tests, extra, probed = setOf("Boom")))
        assertEquals(Outcome.Survived, baseline.outcome.withoutCause())
    }

    @Test
    fun `a baseline without instrumented classes has no coverage`() {
        val fixture = coverageFixture("class ATest { @Test fun adds() { Calc().add(2, 3) } }")
        val executor = JUnitTestExecutor(fixture.project, fixture.tests, runtimeJars()).also { executors += it }
        assertNull(executor.baseline(deadline).coverage)
    }
}
