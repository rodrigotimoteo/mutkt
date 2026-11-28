package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JUnitTestExecutorCoverageTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private fun baseline(
        fixture: CoverageFixture,
        excludeTags: List<String> = emptyList(),
        engines: List<String> = listOf(JUPITER_ENGINE),
    ) = JUnitTestExecutor(fixture.project, fixture.tests, runtimeJars(), engines, excludeTags = excludeTags)
        .also { executors += it }
        .baseline(deadline, fixture.instrumented)

    private fun Coverage.hits() = tests.map { it.test to it.hits }

    @Test
    fun `each test method owns the probes it reached, after its class`() {
        val fixture =
            coverageFixture(
                "class ATest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }\n" +
                    "class BTest { @Test fun subtracts() { assertEquals(1, Calc().sub(3, 2)) } }\n" +
                    "class CTest { @Test fun nothing() {} }",
            )
        val baseline = baseline(fixture)
        assertEquals(Outcome.Survived, baseline.outcome)
        val coverage = baseline.coverage!!
        val expected =
            listOf(
                TestId("ATest") to setOf(),
                method("ATest", "adds") to setOf(ADD),
                TestId("BTest") to setOf(),
                method("BTest", "subtracts") to setOf(SUB),
                TestId("CTest") to setOf(),
                method("CTest", "nothing") to setOf(),
            )
        assertEquals(expected, coverage.hits())
        assertEquals(emptySet<Probe>(), coverage.global)
        assertEquals(emptySet<String>(), coverage.globalReach)
        val reached = coverage.tests.filter { it.test.isClass }.map { it.test.className to it.reached }
        assertEquals(listOf("ATest" to setOf("Calc"), "BTest" to setOf("Calc"), "CTest" to setOf()), reached)
    }

    @Test
    fun `a probe reached outside any test method belongs to its class`() {
        val test =
            "class ATest { companion object { @JvmStatic @BeforeAll fun setUp() { Calc().sub(3, 2) } }\n" +
                "@Test fun adds() { Calc().add(2, 3) } }"
        val expected = listOf(TestId("ATest") to setOf(SUB), method("ATest", "adds") to setOf(ADD))
        assertEquals(expected, baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `a nested test class's methods belong to its enclosing root class`() {
        val test = "class OuterTest { @Nested inner class Inner { @Test fun adds() { Calc().add(2, 3) } } }"
        val inner = TestId("OuterTest", "[engine:junit-jupiter]/[class:OuterTest]/[nested-class:Inner]/[method:adds()]")
        val expected = listOf(TestId("OuterTest") to setOf(), inner to setOf(ADD))
        assertEquals(expected, baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `a repeated test is one method, with the probes of every repetition`() {
        val test =
            "class ATest { @RepeatedTest(2) fun calcs(info: RepetitionInfo) { " +
                "if (info.currentRepetition == 1) Calc().add(2, 3) else Calc().sub(3, 2) } }"
        val template =
            TestId(
                "ATest",
                "[engine:junit-jupiter]/[class:ATest]/[test-template:calcs(org.junit.jupiter.api.RepetitionInfo)]",
            )
        val expected = listOf(TestId("ATest") to setOf(), template to setOf(ADD, SUB))
        assertEquals(expected, baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `a class with one instance for all its methods is covered per class only`() {
        val test =
            "@TestInstance(TestInstance.Lifecycle.PER_CLASS)\nclass ATest { @Test fun adds() { Calc().add(2, 3) } }"
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `a class whose nested class orders its methods is covered per class only`() {
        val test =
            "class ATest { @Nested @TestMethodOrder(MethodOrderer.OrderAnnotation::class) inner class Inner {\n" +
                "@Test fun adds() { Calc().add(2, 3) } } }"
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), baseline(coverageFixture(test)).coverage!!.hits())
    }

    @Test
    fun `one instance per class by default covers every class per class only`(
        @TempDir resources: Path,
    ) {
        Files.writeString(resources.resolve("junit-platform.properties"), "$LIFECYCLE_DEFAULT = per_class\n")
        val fixture = coverageFixture("class ATest { @Test fun adds() { Calc().add(2, 3) } }")
        val coverage =
            JUnitTestExecutor(fixture.project, fixture.tests, runtimeJars() + listOf(resources))
                .also { executors += it }
                .baseline(deadline, fixture.instrumented)
                .coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), coverage.hits())
    }

    @Test
    fun `an engine other than Jupiter is covered per test class only`() {
        val test = "class ATest { @org.junit.Test fun adds() { Calc().add(2, 3) } }"
        val coverage = baseline(coverageFixture(test), engines = listOf("junit-vintage")).coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(ADD)), coverage.hits())
    }

    @Test
    fun `disabled and tag-filtered test classes are absent`() {
        val tests =
            "class ATest { @Test fun adds() { Calc().add(2, 3) } }\n" +
                "@Disabled class DTest { @Test fun subtracts() { Calc().sub(3, 2) } }\n" +
                "@Tag(\"slow\") class STest { @Test fun subtracts() { Calc().sub(3, 2) } }"
        val coverage = baseline(coverageFixture(tests), excludeTags = listOf("slow")).coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(), method("ATest", "adds") to setOf(ADD)), coverage.hits())
        assertEquals(emptySet<Probe>(), coverage.global)
    }

    @Test
    fun `a probe and a class reached while discovering the tests are global`() {
        val generator =
            "class Names : DisplayNameGenerator.Standard() { " +
                "override fun generateDisplayNameForClass(type: Class<*>) = \"n\" + Calc().sub(3, 2) }\n"
        val test = "@DisplayNameGeneration(Names::class) class ATest { @Test fun adds() { Calc().add(2, 3) } }"
        val coverage = baseline(coverageFixture(generator + test)).coverage!!
        assertEquals(listOf(TestId("ATest") to setOf(), method("ATest", "adds") to setOf(ADD)), coverage.hits())
        assertEquals(setOf(SUB), coverage.global)
        assertEquals(setOf("Calc"), coverage.globalReach)
    }

    @Test
    fun `a probe reached in a copy of the class from another loader counts`() {
        val baseline = baseline(coverageFixture(SANDBOX_TEST))
        assertEquals(Outcome.Survived, baseline.outcome)
        assertEquals(
            listOf(TestId("ATest") to setOf(), method("ATest", "adds") to setOf(ADD)),
            baseline.coverage!!.hits(),
        )
    }

    @Test
    fun `a baseline without instrumented classes has no coverage`() {
        val fixture = coverageFixture("class ATest { @Test fun adds() { Calc().add(2, 3) } }")
        val executor = JUnitTestExecutor(fixture.project, fixture.tests, runtimeJars()).also { executors += it }
        assertNull(executor.baseline(deadline).coverage)
    }
}
