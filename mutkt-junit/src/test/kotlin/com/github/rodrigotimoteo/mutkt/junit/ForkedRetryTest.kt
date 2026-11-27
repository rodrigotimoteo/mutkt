package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

// State an earlier run left in a reused child (memory, a JVM-wide property) must not decide a later mutant's status.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedRetryTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private fun pool(
        test: String,
        tweak: (ForkSettings) -> ForkSettings = { it },
    ) = ForkedTestExecutor(tweak(forkSettingsFor(dir, test))).also { executor = it }

    @Test
    fun `an OutOfMemoryError in a reused child is retried on a fresh child, whose result counts`() {
        // The first run in a child keeps 100 MiB (well under the 75% retirement threshold) reachable from the JVM-wide
        // system properties; a later run there asks for 200 MiB more, which cannot fit in 256 MiB.
        val test =
            "class CalcTest { @Test fun adds() { val props = System.getProperties(); " +
                "if (props[\"mutkt.hog\"] != null) ByteArray(200 shl 20) " +
                "else props[\"mutkt.hog\"] = ByteArray(100 shl 20); " +
                "assertEquals(5, Calc().add(2, 3)) } }"
        val pool = pool(test) { it.copy(jvmArgs = listOf("-Xmx256m")) }
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        // Not retired: the mutant below runs on this child first.
        val first = pool.liveProcesses.single()

        assertKilledBy("CalcTest.adds", pool.run(mutantOf(), mutantBytes(), 60.seconds))
        assertFalse(first.isAlive)
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `a timeout in a reused child is retried on a fresh child, whose result counts`() {
        val test =
            "class CalcTest { @Test fun adds() { " +
                "if (System.getProperty(\"mutkt.reused\") != null) while (System.nanoTime() > 0) {}; " +
                "System.setProperty(\"mutkt.reused\", \"yes\"); assertEquals(5, Calc().add(2, 3)) } }"
        val pool = pool(test)
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))

        assertKilledBy("CalcTest.adds", pool.run(mutantOf(), mutantBytes(), 2.seconds))
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `three mutants in a row with no Started within the preparation limit stop the analysis, naming the limit`() {
        // Jupiter instantiates the display name generator while it discovers CalcTest, so every child stalls.
        val test =
            "class Stall : DisplayNameGenerator.Standard() { init { Thread.sleep(Long.MAX_VALUE) } }\n" +
                "@DisplayNameGeneration(Stall::class) class CalcTest { @Test fun adds() {} }"
        val pool = pool(test) { it.copy(prepareMillis = PREPARE) }
        repeat(2) {
            val error = assertThrows<IllegalStateException> { pool.run(mutantOf(), mutantBytes(), 1.seconds) }
            assertTrue("no Started within $PREPARE ms" in error.message.orEmpty(), error.message)
        }

        val stop = assertThrows<TestExecutionStopped> { pool.run(mutantOf(), mutantBytes(), 1.seconds) }
        val expected =
            "Forked test JVMs did not finish loading and discovering the tests within $PREPARE ms " +
                "for 3 mutants in a row, so the analysis stopped. Last child output: "
        assertTrue(stop.message.orEmpty().startsWith(expected), stop.message)
        assertThrows<TestExecutionStopped> { pool.run(mutantOf(), mutantBytes(), 1.seconds) }
        assertEquals(3, pool.startedChildren)
    }

    private companion object {
        const val PREPARE = 3_000L
    }
}
