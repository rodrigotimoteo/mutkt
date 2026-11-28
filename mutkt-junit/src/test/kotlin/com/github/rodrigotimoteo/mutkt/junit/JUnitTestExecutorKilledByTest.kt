package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
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
        assertKilledBy("CalcTest", killedBy(tests))
    }

    // The class is what a run selects, so a failure in a @Nested class names its enclosing class.
    @Test
    fun `a kill names the selectable test class that failed, enclosing a nested one`() {
        val tests =
            "class CalcTest { @Nested inner class Sums { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } } }"
        assertEquals(TestId("CalcTest"), (killedBy(tests) as Outcome.Killed).test)
    }

    @Test
    fun `an inherited test names the class that ran it`() {
        val tests =
            "abstract class BaseTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } } " +
                "class CalcTest : BaseTest()"
        assertEquals(TestId("CalcTest"), (killedBy(tests) as Outcome.Killed).test)
    }
}
