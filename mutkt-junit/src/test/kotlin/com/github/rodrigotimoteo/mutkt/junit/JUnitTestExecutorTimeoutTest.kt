package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class JUnitTestExecutorTimeoutTest {
    private val executor =
        executorFor("$IMPORTS$CALC\nclass CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }")

    @AfterEach
    fun cleanUp() {
        executor.close()
        System.clearProperty(WORKER)
    }

    private fun run(
        bytes: ByteArray,
        deadline: Duration = SHORT,
    ) = executor.run(mutantOf(), bytes, deadline)

    @Test
    fun `endless mutant times out shortly after the deadline`() {
        val (outcome, elapsed) = measureTimedValue { run(SPIN) }
        assertEquals(Outcome.Timeout, outcome)
        assertTrue(elapsed >= SHORT, "returned after $elapsed")
        assertTrue(elapsed < 10.seconds, "returned after $elapsed")
    }

    @Test
    fun `worker is a named daemon thread other than the caller`() {
        run(SPIN)
        val (name, daemon) = awaitWorker().split("|")
        assertTrue(name.startsWith("mutkt-mutant-") && name != Thread.currentThread().name, name)
        assertEquals("true", daemon)
    }

    @Test
    fun `timed out worker is interrupted and terminates`() {
        run(SPIN)
        val name = awaitWorker().substringBefore("|")
        awaitGone(name)
    }

    @Test
    fun `executor keeps working after a timeout`() {
        assertEquals(Outcome.Timeout, run(SPIN))
        assertEquals(Outcome.Killed("CalcTest.adds"), run(mutantBytes()))
        assertEquals(Outcome.Survived, run(mutantBytes(CALC)))
    }

    @Test
    fun `a timeout does not affect a concurrent run`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val timedOut = pool.submit<Outcome> { run(SPIN) }
            val survived = pool.submit<Outcome> { run(mutantBytes(CALC), 30.seconds) }
            assertEquals(Outcome.Timeout, timedOut.get(60, TimeUnit.SECONDS))
            assertEquals(Outcome.Survived, survived.get(60, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `finishing within the deadline is not a timeout`() {
        assertEquals(Outcome.Survived, run(mutantBytes(CALC), 30.seconds))
    }

    @Test
    fun `deadline must be positive`() {
        listOf(Duration.ZERO, (-1).milliseconds).forEach {
            val error = assertThrows<IllegalArgumentException> { run(mutantBytes(CALC), it) }
            assertTrue("positive" in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun `interrupting the caller cancels the worker`() {
        val failure = AtomicReference<Throwable>()
        val caller = Thread { runCatching { run(SPIN, 60.seconds) }.onFailure(failure::set) }.apply { start() }
        val name = awaitWorker().substringBefore("|")
        caller.interrupt()
        caller.join(10_000)
        assertTrue(failure.get() is InterruptedException, failure.get().toString())
        awaitGone(name)
    }

    private fun awaitWorker(): String {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.getProperty(WORKER) == null && System.nanoTime() < limit) Thread.sleep(10)
        return checkNotNull(System.getProperty(WORKER)) { "worker never started" }
    }

    private fun awaitGone(name: String) {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (Thread.getAllStackTraces().keys.any { it.name == name } && System.nanoTime() < limit) Thread.sleep(10)
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == name }, "worker $name still alive")
    }

    private companion object {
        const val WORKER = "mutkt.test.worker"
        val SHORT = 1500.milliseconds

        // Spins until interrupted so that abandoned workers die and do not pile up across test runs.
        val SPIN =
            mutantBytes(
                "class Calc { fun add(a: Int, b: Int): Int { val t = Thread.currentThread(); " +
                    "System.setProperty(\"$WORKER\", t.name + \"|\" + t.isDaemon); " +
                    "while (!t.isInterrupted) {}; return 0 } }",
            )
    }
}
