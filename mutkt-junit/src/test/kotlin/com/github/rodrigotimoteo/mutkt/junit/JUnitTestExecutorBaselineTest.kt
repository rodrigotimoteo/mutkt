package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JUnitTestExecutorBaselineTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private val tests =
        "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } " +
            "@Tag(\"flaky\") @Test fun unrelated() { fail<Unit>(\"red\") } }"

    private fun executor(
        source: String,
        exclude: List<String> = emptyList(),
    ): JUnitTestExecutor {
        val (project, classes) = splitClasses("$IMPORTS$CALC\n$source")
        return JUnitTestExecutor(project, classes, runtimeJars(), excludeTags = exclude).also { executors += it }
    }

    @Test
    fun `a green suite survives the baseline`() {
        val executor = executor("class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }")
        assertEquals(Outcome.Survived, executor.baseline(deadline).outcome)
    }

    @Test
    fun `an unrelated failing assertion is reported by name`() {
        val killed =
            Outcome.Killed(
                "CalcTest.unrelated",
                KillKind.ASSERTION,
                "org.opentest4j.AssertionFailedError: red",
                TestId("CalcTest"),
            )
        assertEquals(killed, executor(tests).baseline(deadline).outcome)
    }

    @Test
    fun `the cause of a red baseline is one line and capped`() {
        val long = "class CalcTest { @Test fun long() { throw IllegalStateException(\"x\\n\".repeat(100)) } }"
        val cause = (executor(long).baseline(deadline).outcome as Outcome.Killed).cause.orEmpty()
        assertEquals(200, cause.length)
        assertTrue(cause.startsWith("java.lang.IllegalStateException: x x"), cause)
        assertTrue(cause.endsWith("…"), cause)
        assertFalse('\n' in cause, cause)
    }

    @Test
    fun `the same assertion excluded by tag does not fail the baseline`() {
        assertEquals(Outcome.Survived, executor(tests, exclude = listOf("flaky")).baseline(deadline).outcome)
    }

    @Test
    fun `a failing container is reported by class name`() {
        val failing =
            "class CalcTest { companion object { " +
                "@BeforeAll @JvmStatic fun setUp() { error(\"boom\") } } @Test fun a() {} }"
        assertKilledBy("CalcTest", executor(failing).baseline(deadline).outcome)
    }

    @Test
    fun `no tests means no tests`() {
        assertEquals(Outcome.NoTests, executor("class CalcTest { fun adds() {} }").baseline(deadline).outcome)
    }

    @Test
    fun `a baseline that outlives the deadline times out`() {
        val slow = "class CalcTest { @Test fun slow() { Thread.sleep(30_000) } }"
        assertEquals(Outcome.Timeout, executor(slow).baseline(1.seconds).outcome)
    }

    @Test
    fun `measures how long the tests ran, from the start of execution`() {
        val slow = "class CalcTest { @Test fun slow() { Thread.sleep(500) } }"
        val elapsed = executor(slow).baseline(deadline).elapsed
        assertTrue(elapsed >= 500.milliseconds && elapsed < deadline, "elapsed $elapsed")
    }

    // Main's Calc fails when run; the test sources define their own Calc, which is the one the tests load.
    private fun shadowed(): Pair<ClassFile, JUnitTestExecutor> {
        val failing = compileKotlin("class Calc { fun add(a: Int, b: Int): Int = error(\"main\") }")
        val main = ClassFile("Calc", failing.getValue("Calc"))
        val test = "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"
        val (_, testClasses) = splitClasses("$IMPORTS$CALC\n$test", production = emptySet())
        return main to JUnitTestExecutor(listOf(main), testClasses, runtimeJars()).also { executors += it }
    }

    @Test
    fun `a probed main class never replaces the test class it shadows`() {
        val (main, executor) = shadowed()
        val baseline = executor.baseline(deadline, Instrumented(listOf(main), emptyMap()))
        assertEquals(Outcome.Survived, baseline.outcome)
        // One entry for the class and one for its method: neither reached the shadowed main class.
        assertEquals(true, baseline.coverage?.tests?.all { it.reached.isEmpty() })
        assertEquals(true, baseline.coverage?.tests?.isNotEmpty())
    }

    @Test
    fun `a mutant of a main class shadowed by a test class reaches no test`() {
        val (main, executor) = shadowed()
        assertEquals(Outcome.NoTests, executor.run(mutantOf(), main.bytes, deadline))
    }

    @Test
    fun `is rejected after close`() {
        val executor = executor(tests)
        executor.close()
        assertThrows<IllegalStateException> { executor.baseline(deadline) }
    }
}
