package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedTestExecutorTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private fun executor(
        parallelism: Int = 1,
        testSource: String = CALC_TEST,
        tweak: (ForkSettings) -> ForkSettings = { it },
    ): ForkedTestExecutor {
        val settings = forkSettingsFor(dir, testSource, parallelism = parallelism)
        return ForkedTestExecutor(tweak(settings)).also { executor = it }
    }

    private fun ForkedTestExecutor.run(
        bytes: ByteArray,
        deadline: Duration = LONG,
    ) = run(mutantOf(), bytes, deadline)

    @Test
    fun `kills and survives through the pool, reusing one child`() {
        val pool = executor()
        assertEquals(Outcome.Killed("CalcTest.adds"), pool.run(mutantBytes()))
        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
        assertEquals(1, pool.startedChildren)
        assertNotEquals(ProcessHandle.current().pid(), pool.liveProcesses.single().pid())
    }

    @Test
    fun `a timed out child is killed and the next mutant gets a fresh one`() {
        val pool = executor()
        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
        val first = pool.liveProcesses.single()

        assertEquals(Outcome.Timeout, pool.run(SPIN, 1.seconds))
        assertFalse(first.isAlive)
        assertTrue(pool.liveProcesses.isEmpty())

        assertEquals(Outcome.Killed("CalcTest.adds"), pool.run(mutantBytes()))
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `a stall before execution fails with a diagnostic, frees the slot and lets the next mutant complete`() {
        val arm = dir.resolve("arm")
        val agent = stallAgentJar(dir.resolve("agent.jar"))
        val pool = executor { it.copy(jvmArgs = listOf("-javaagent:$agent=$arm"), prepareMillis = PREPARE) }
        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
        Files.createFile(arm)

        val error = assertThrows<IllegalStateException> { pool.run(mutantBytes(), 1.seconds) }
        assertTrue("no Started within $PREPARE ms" in error.message.orEmpty(), error.message)
        assertTrue(pool.liveProcesses.isEmpty())

        assertEquals(KILLED, pool.run(mutantBytes()))
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `a stall in test discovery fails with the preparation diagnostic, not a timeout`() {
        // Jupiter instantiates the display name generator while it discovers CalcTest.
        val test =
            "class Stall : DisplayNameGenerator.Standard() { init { Thread.sleep(Long.MAX_VALUE) } }\n" +
                "@DisplayNameGeneration(Stall::class) $CALC_TEST"
        val pool = executor(testSource = test) { it.copy(prepareMillis = PREPARE) }

        val error = assertThrows<IllegalStateException> { pool.run(mutantBytes(), 1.seconds) }
        assertTrue("no Started within $PREPARE ms" in error.message.orEmpty(), error.message)
        assertTrue(pool.liveProcesses.isEmpty())
    }

    @Test
    fun `a hang once execution started is a timeout, and the preparation limit does not cut execution short`() {
        val pool = executor { it.copy(prepareMillis = PREPARE, graceMillis = 500) }
        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
        // Deadline plus grace is longer than the preparation limit: a Timeout proves the wait outlived it.
        assertEquals(Outcome.Timeout, pool.run(SPIN, (PREPARE + 1000).milliseconds))
        assertTrue(pool.liveProcesses.isEmpty())

        assertEquals(KILLED, pool.run(mutantBytes()))
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `a crashing mutant throws with the exit code, then the pool recovers`() {
        val pool = executor()
        val error = assertThrows<IllegalStateException> { pool.run(EXIT) }
        assertTrue(
            error.message.orEmpty().startsWith(
                "Forked test JVM failed (exit code 7) while running a mutant. Last stderr:",
            ),
            error.message,
        )
        assertTrue(pool.liveProcesses.isEmpty())

        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
        assertEquals(2, pool.startedChildren)
    }

    @Test
    fun `concurrent mutants use exactly the parallelism, never more`() {
        val pool = executor(parallelism = 2)
        val peak = AtomicInteger()
        val sampler =
            thread(isDaemon = true) {
                while (!Thread.currentThread().isInterrupted) {
                    peak.accumulateAndGet(pool.liveProcesses.size, ::maxOf)
                    Thread.sleep(10)
                }
            }
        val threads = Executors.newFixedThreadPool(4)
        try {
            val results =
                (0 until 4)
                    .map { i -> threads.submit(Callable { pool.run(if (i % 2 == 0) SLOW_SURVIVES else SLOW_KILLED) }) }
                    .map { it.get(90, TimeUnit.SECONDS) }
            assertEquals(listOf(Outcome.Survived, KILLED, Outcome.Survived, KILLED), results)
            assertEquals(2, peak.get())
            assertEquals(2, pool.startedChildren)
        } finally {
            sampler.interrupt()
            threads.shutdownNow()
        }
    }

    @Test
    fun `close leaves no live child and rejects further runs`() {
        val pool = executor(parallelism = 2)
        pool.run(mutantBytes(CALC))
        val processes = pool.liveProcesses
        assertEquals(1, processes.size)

        pool.close()
        assertTrue(processes.none { it.isAlive })
        assertTrue(pool.liveProcesses.isEmpty())
        val error = assertThrows<IllegalStateException> { pool.run(mutantBytes(CALC)) }
        assertTrue("is closed" in error.message.orEmpty(), error.message)
        assertEquals(1, pool.startedChildren)
    }

    @Test
    fun `closing during a run kills the busy child and reports closed, not a crash`() {
        val pool = executor()
        pool.run(mutantBytes(CALC))
        val busy = Executors.newSingleThreadExecutor()
        try {
            val running = busy.submit(Callable { pool.run(SPIN, LONG) })
            Thread.sleep(1000)
            pool.close()
            val error = assertThrows<ExecutionException> { running.get(30, TimeUnit.SECONDS) }
            assertTrue("is closed" in error.cause?.message.orEmpty(), error.cause?.message)
            assertTrue(pool.liveProcesses.isEmpty())
        } finally {
            busy.shutdownNow()
        }
    }

    @Test
    fun `the child sees the configured environment and working directory`() {
        val cwd = Files.createDirectory(dir.resolve("cwd")).toRealPath()
        val test =
            "class CalcTest { @Test fun env() { assertEquals(\"v1\", System.getenv(\"MUTKT_FORK_VAR\")); " +
                "assertEquals(\"$cwd\", java.io.File(\"\").absoluteFile.toPath().toRealPath().toString()) } }"
        val pool =
            executor(testSource = test) { it.copy(environment = mapOf("MUTKT_FORK_VAR" to "v1"), workingDir = cwd) }
        assertEquals(Outcome.Survived, pool.run(mutantBytes(CALC)))
    }

    @Test
    fun `a child that cannot boot reports its exit code and stderr, and three failures in a row stop retries`() {
        val pool = executor { it.copy(jvmArgs = listOf("-XX:+NoSuchMutktOption")) }
        repeat(3) {
            val error = assertThrows<IllegalStateException> { pool.run(mutantBytes(CALC)) }
            val message = error.message.orEmpty()
            assertTrue("exit code 1" in message && "NoSuchMutktOption" in message, message)
        }
        val giveUp = assertThrows<IllegalStateException> { pool.run(mutantBytes(CALC)) }
        assertTrue("3 times in a row" in giveUp.message.orEmpty(), giveUp.message)
        assertEquals(0, pool.startedChildren)
    }

    @Test
    fun `tag and engine settings reach the child`() {
        val test =
            "class CalcTest { @Tag(\"fast\") @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } @Test fun ok() {} }"
        val killed = Outcome.Killed("CalcTest.adds")
        assertEquals(killed, executor(testSource = test).run(mutantBytes()))

        val excluded = executor(testSource = test) { it.copy(excludeTags = listOf("fast")) }
        assertEquals(Outcome.Survived, excluded.run(mutantBytes()))
        val included = executor(testSource = test) { it.copy(includeTags = listOf("fast")) }
        assertEquals(killed, included.run(mutantBytes()))
        val vintage = executor(testSource = test) { it.copy(engines = listOf("junit-vintage")) }
        val error = assertThrows<IllegalStateException> { vintage.run(mutantBytes()) }
        assertTrue("No TestEngine ID matched" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the baseline runs in the configured fork environment and reuses the child of later mutants`() {
        val test =
            "class CalcTest { @Test fun env() { assertEquals(\"v1\", System.getenv(\"MUTKT_FORK_VAR\")) } " +
                "@Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"
        val pool = executor(testSource = test) { it.copy(environment = mapOf("MUTKT_FORK_VAR" to "v1")) }
        assertEquals(Outcome.Survived, pool.baseline(LONG).outcome)
        assertEquals(Outcome.Killed("CalcTest.adds"), pool.run(mutantBytes()))
        assertEquals(1, pool.startedChildren)
    }

    @Test
    fun `a red baseline names the unrelated failure unless a tag excludes it`() {
        val test =
            "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } " +
                "@Tag(\"slow\") @Test fun unrelated() { fail<Unit>(\"red\") } }"
        assertEquals(Outcome.Killed("CalcTest.unrelated"), executor(testSource = test).baseline(LONG).outcome)
        val excluded = executor(testSource = test) { it.copy(excludeTags = listOf("slow")) }
        assertEquals(Outcome.Survived, excluded.baseline(LONG).outcome)
    }

    @Test
    fun `the baseline reports how long the child ran the tests`() {
        val pool = executor(testSource = "class CalcTest { @Test fun slow() { Thread.sleep(500) } }")
        val elapsed = pool.baseline(LONG).elapsed
        assertTrue(elapsed >= 500.milliseconds && elapsed < LONG, "elapsed $elapsed")
    }

    @Test
    fun `a baseline that outlives its deadline kills the child`() {
        val pool = executor(testSource = "class CalcTest { @Test fun spin() { while (System.nanoTime() > 0) {} } }")
        assertEquals(Outcome.Timeout, pool.baseline(1.seconds).outcome)
        assertTrue(pool.liveProcesses.isEmpty())
    }

    @Test
    fun `a missing java executable fails with a clear message`() {
        val pool = executor { it.copy(javaExecutable = dir.resolve("no-java")) }
        val error = assertThrows<IllegalStateException> { pool.run(mutantBytes(CALC)) }
        assertTrue("Cannot start forked test JVM" in error.message.orEmpty(), error.message)
    }

    private companion object {
        const val PREPARE = 3_000L

        // Sleeps so that the children of a parallel run overlap.
        fun slowCalc(body: String) =
            mutantBytes("class Calc { fun add(a: Int, b: Int): Int { Thread.sleep(500); return $body } }")

        val LONG = 60.seconds
        val KILLED = Outcome.Killed("CalcTest.adds")
        val javaBinary: Path = Path.of(System.getProperty("java.home"), "bin", "java")
        const val CALC_TEST = "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"

        // Not interruptible and not a safepoint-free loop the JVM can optimise away: only a kill stops it.
        val SPIN =
            mutantBytes(
                "class Calc { fun add(a: Int, b: Int): Int { while (System.nanoTime() > 0) {}; return 0 } }",
            )
        val SLOW_SURVIVES = slowCalc("a + b")
        val SLOW_KILLED = slowCalc("a - b")
        val EXIT = mutantBytes("class Calc { fun add(a: Int, b: Int): Int { System.exit(7); return 0 } }")
    }
}
