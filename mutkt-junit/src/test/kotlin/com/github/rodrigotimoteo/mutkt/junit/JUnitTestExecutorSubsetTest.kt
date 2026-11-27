package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JUnitTestExecutorSubsetTest {
    private val executor =
        executorFor(
            "$IMPORTS$CALC\n" +
                "class ATest { @Test fun a() { fail<Unit>(\"a\") } }\n" +
                "class BTest { @Test fun b() { assertEquals(5, Calc().add(2, 3)) } }\n" +
                "class CTest { @Test fun c() { fail<Unit>(\"c\") } }",
        )

    @AfterEach
    fun closeExecutor() = executor.close()

    private fun run(vararg tests: String) = executor.run(mutantOf(), mutantBytes(CALC), deadline, tests.map(::TestId))

    @Test
    fun `runs only the given test classes`() {
        assertEquals(Outcome.Survived, run("BTest"))
    }

    @Test
    fun `runs the given test classes in the given order`() {
        assertKilledBy("CTest.c", run("BTest", "CTest", "ATest"))
        assertKilledBy("ATest.a", run("ATest", "CTest"))
    }

    @Test
    fun `no test classes means no tests`() {
        assertEquals(Outcome.NoTests, run())
    }

    @Test
    fun `an unknown test class is rejected`() {
        val error = assertThrows<IllegalArgumentException> { run("DTest") }
        assertEquals("No test class DTest", error.message)
    }
}
