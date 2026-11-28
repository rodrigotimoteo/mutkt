package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val PARAMS_IMPORTS = "import org.junit.jupiter.params.*\nimport org.junit.jupiter.params.provider.*\n"

class JUnitTestExecutorKilledByTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private fun killedBy(tests: String): Outcome {
        val executor = executorFor("$IMPORTS$PARAMS_IMPORTS$CALC\n$tests")
        executors += executor
        return executor.run(mutantOf(), mutantBytes(), deadline)
    }

    @Test
    fun `a parameterized invocation is told apart from its siblings`() {
        val tests =
            "class CalcTest { @ParameterizedTest @CsvSource(\"1,0,1\", \"2,2,4\") " +
                "fun adds(a: Int, b: Int, sum: Int) { assertEquals(sum, Calc().add(a, b)) } }"
        assertKilledBy("CalcTest.adds[#2] [2] 2, 2, 4", killedBy(tests))
    }

    @Test
    fun `a dynamic test is told apart from the factory`() {
        val tests =
            "class CalcTest { @TestFactory fun adds() = listOf(0, 2).mapIndexed { i, b -> " +
                "DynamicTest.dynamicTest(\"case \$i\") { assertEquals(b + 1, Calc().add(1, b)) } } }"
        assertKilledBy("CalcTest.adds[#2] case 1", killedBy(tests))
    }

    @Test
    fun `repeated custom names and line breaks stay distinct and on one line`() {
        val tests =
            "class CalcTest { @ParameterizedTest(name = \"same\\nname\") @CsvSource(\"1,0,1\", \"2,2,4\") " +
                "fun adds(a: Int, b: Int, sum: Int) { assertEquals(sum, Calc().add(a, b)) } }"
        assertKilledBy("CalcTest.adds[#2] same name", killedBy(tests))
    }

    @Test
    fun `a failing container is named by its class`() {
        val tests =
            "class CalcTest { companion object { " +
                "@BeforeAll @JvmStatic fun setUp() { check(Calc().add(1, 1) == 2) } } @Test fun adds() {} }"
        val outcome = killedBy(tests)
        assertKilledBy("CalcTest", outcome)
        assertEquals(KillKind.EXCEPTION, (outcome as Outcome.Killed).kind)
    }

    @Test
    fun `a failed assertion kills as an assertion`() {
        val tests = "class CalcTest { @Test fun adds() { assertEquals(2, Calc().add(1, 1)) } }"
        assertEquals(KillKind.ASSERTION, kindOf(killedBy(tests)))
    }

    @Test
    fun `a JUnit 4 assertion on the vintage engine kills as an assertion`() {
        val tests =
            "class CalcTest { @org.junit.Test fun adds() { " +
                "org.junit.Assert.assertEquals(2, Calc().add(1, 1)) } }"
        val (project, testClasses) = splitClasses("$CALC\n$tests")
        val executor = JUnitTestExecutor(project, testClasses, runtimeJars(), listOf("junit-vintage"))
        executors += executor
        assertEquals(KillKind.ASSERTION, kindOf(executor.run(mutantOf(), mutantBytes(), deadline)))
    }

    // Kotest is not a test dependency: this stands in for its MultiAssertionError, an AssertionError subclass.
    @Test
    fun `a subclass of AssertionError kills as an assertion`() {
        val tests =
            "class MultiAssertionError : AssertionError(\"mismatch\")\n" +
                "class CalcTest { @Test fun adds() { if (Calc().add(1, 1) != 2) throw MultiAssertionError() } }"
        assertEquals(KillKind.ASSERTION, kindOf(killedBy(tests)))
    }

    @Test
    fun `an assertion wrapped in another exception kills as an assertion`() {
        val tests =
            "class CalcTest { @Test fun adds() { if (Calc().add(1, 1) != 2) " +
                "throw java.util.concurrent.ExecutionException(AssertionError(\"mismatch\")) } }"
        assertEquals(KillKind.ASSERTION, kindOf(killedBy(tests)))
    }

    @Test
    fun `a cause chain that loops kills as an exception`() {
        val tests =
            "class CalcTest { @Test fun adds() { if (Calc().add(1, 1) != 2) { " +
                "val a = IllegalStateException(\"a\"); a.initCause(IllegalStateException(\"b\", a)); throw a } } }"
        assertEquals(KillKind.EXCEPTION, kindOf(killedBy(tests)))
    }

    @Test
    fun `an exception that is not an assertion kills as an exception`() {
        val tests = "class CalcTest { @Test fun adds() { listOf(1)[Calc().add(1, 1) - 2] } }"
        assertEquals(KillKind.EXCEPTION, kindOf(killedBy(tests)))
    }

    private fun kindOf(outcome: Outcome) = (outcome as Outcome.Killed).kind
}
