package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        assertEquals(Outcome.Survived, executor.baseline(deadline))
    }

    @Test
    fun `an unrelated failing assertion is reported by name`() {
        assertEquals(Outcome.Killed("CalcTest.unrelated"), executor(tests).baseline(deadline))
    }

    @Test
    fun `the same assertion excluded by tag does not fail the baseline`() {
        assertEquals(Outcome.Survived, executor(tests, exclude = listOf("flaky")).baseline(deadline))
    }

    @Test
    fun `a failing container is reported by class name`() {
        val failing =
            "class CalcTest { companion object { " +
                "@BeforeAll @JvmStatic fun setUp() { error(\"boom\") } } @Test fun a() {} }"
        assertEquals(Outcome.Killed("CalcTest"), executor(failing).baseline(deadline))
    }

    @Test
    fun `no tests means no tests`() {
        assertEquals(Outcome.NoTests, executor("class CalcTest { fun adds() {} }").baseline(deadline))
    }

    @Test
    fun `a baseline that outlives the deadline times out`() {
        val slow = "class CalcTest { @Test fun slow() { Thread.sleep(30_000) } }"
        assertEquals(Outcome.Timeout, executor(slow).baseline(1.seconds))
    }

    @Test
    fun `is rejected after close`() {
        val executor = executor(tests)
        executor.close()
        assertThrows<IllegalStateException> { executor.baseline(deadline) }
    }
}
