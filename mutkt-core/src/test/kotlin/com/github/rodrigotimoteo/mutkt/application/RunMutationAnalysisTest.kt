package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import com.github.rodrigotimoteo.mutkt.domain.DeadlineCap
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val WAIT_SECONDS = 10L

// What ChildProcess throws for a child that died before the tests started; the stderr tail may hold paths and is not
// part of the cause.
private const val FORK_CRASH =
    "Forked test JVM failed (exit code 7) while running a mutant. Last stderr: /home/dev/secret\nmore"

private fun fail(error: Throwable): Nothing = throw error

class RunMutationAnalysisTest {
    private val writer = RecordingWriter()

    private fun analysis(
        mutants: List<Mutant>,
        executor: FakeExecutor = FakeExecutor(),
        generator: FakeGenerator = FakeGenerator(mutants),
        filters: ClassFilters = ClassFilters(),
        parallelism: Int = 4,
    ) = RunMutationAnalysis(
        FakeClassSource(mutants.map { it.className }.distinct().map(::classFile)),
        generator,
        executor,
        writer,
        AnalysisConfig(filters, 7.seconds, parallelism),
    )

    private fun statuses(vararg outcomes: Outcome): List<MutantStatus> {
        val mutants = outcomes.indices.map { mutant(ordinal = it) }
        val executor = FakeExecutor { outcomes[it.id.ordinal] }
        return analysis(mutants, executor).run().results.map { it.status }
    }

    @Test
    fun `maps every outcome to its status`() =
        assertEquals(
            listOf(
                MutantStatus.KILLED,
                MutantStatus.SURVIVED,
                MutantStatus.TIMEOUT,
                MutantStatus.NO_COVERAGE,
                MutantStatus.ERROR,
            ),
            statuses(Outcome.Killed("t"), Outcome.Survived, Outcome.Timeout, Outcome.NoTests, Outcome.LoadError("x")),
        )

    @Test
    fun `keeps the first failing test only on killed mutants`() {
        val outcomes = listOf(Outcome.Killed("a.FooTest.adds [2] 2, 2, 4"), Outcome.Survived, Outcome.LoadError("x"))
        val mutants = outcomes.indices.map { mutant(ordinal = it) }
        val results = analysis(mutants, FakeExecutor { outcomes[it.id.ordinal] }).run().results
        assertEquals(listOf("a.FooTest.adds [2] 2, 2, 4", null, null), results.map { it.killedBy })
    }

    @Test
    fun `passes materialized bytes and the configured timeout to the executor`() {
        val m = mutant()
        val executor = FakeExecutor()
        analysis(listOf(m), executor).run()
        assertEquals(listOf(Triple(m, m.id.toString(), 7.seconds)), executor.received.toList())
    }

    // Needs parallelism (4) >= mutant count so the first mutant's wait cannot starve the others.
    @Test
    fun `keeps discovery order when mutants finish out of order`() {
        val mutants = (0 until 4).map { mutant(ordinal = it) }
        // Released by progress (the calling thread), i.e. after the others' results were collected; counting
        // down inside the executor would let the first mutant overtake a result still in flight.
        val othersReported = CountDownLatch(mutants.size - 1)
        val executor =
            FakeExecutor {
                if (it == mutants.first()) {
                    assertTrue(othersReported.await(WAIT_SECONDS, TimeUnit.SECONDS))
                }
                Outcome.Survived
            }
        val progress = ConcurrentLinkedQueue<MutantResult>()
        val report =
            analysis(mutants, executor).run {
                progress.add(it)
                othersReported.countDown()
            }
        assertEquals(mutants, report.results.map { it.mutant })
        assertEquals(mutants.first(), progress.last().mutant)
    }

    @Test
    fun `every mutant gets exactly one result even when materialize or run throw`() {
        val random = Random(42)
        val mutants = (0 until 200).map { mutant(ordinal = it) }
        val outcomes =
            mutants.associateWith {
                listOf(Outcome.Survived, Outcome.NoTests, Outcome.Timeout, Outcome.Killed("t"), null).random(random)
            }
        val failing = mutants.filter { random.nextInt(10) == 0 }.toSet()
        val executor = FakeExecutor { outcomes.getValue(it) ?: error("boom") }
        val report = analysis(mutants, executor, FakeGenerator(mutants, failing)).run()
        assertEquals(mutants, report.results.map { it.mutant })
        report.results.forEach {
            val broken = it.mutant in failing || outcomes.getValue(it.mutant) == null
            assertEquals(broken, it.status == MutantStatus.ERROR, it.mutant.toString())
        }
    }

    @Test
    fun `a LinkageError from materialize or run is that mutant's ERROR`() {
        val mutants = (0 until 2).map { mutant(ordinal = it) }
        val executor = FakeExecutor { if (it.id.ordinal == 0) throw VerifyError("bad") else Outcome.Survived }
        val generator = FakeGenerator(mutants, setOf(mutants[1]), NoClassDefFoundError("x"))
        val report = analysis(mutants, executor, generator).run()
        assertEquals(listOf(MutantStatus.ERROR, MutantStatus.ERROR), report.results.map { it.status })
    }

    @Test
    fun `an ERROR keeps a bounded one-line cause and other statuses keep none`() {
        val mutants = (0 until 6).map { mutant("a.".repeat(60) + "Foo", "m".repeat(60), it) }
        val long = "x".repeat(300)
        val executor =
            FakeExecutor {
                when (it.id.ordinal) {
                    0 -> Outcome.LoadError("java.lang.VerifyError: bad\n\tat Foo")
                    1 -> fail(IllegalStateException(FORK_CRASH))
                    2 -> fail(NoClassDefFoundError(long))
                    3 -> fail(IllegalStateException())
                    4 -> fail(IllegalStateException(" "))
                    else -> Outcome.Killed("t")
                }
            }
        val results = analysis(mutants, executor).run().results
        assertEquals(
            listOf(
                "java.lang.VerifyError: bad",
                "Forked test JVM failed (exit code 7) while running a mutant",
                ("java.lang.NoClassDefFoundError: $long").take(200),
                "java.lang.IllegalStateException",
                "java.lang.IllegalStateException:",
                null,
            ),
            results.map { it.detail },
        )
    }

    @Test
    fun `an Error from the executor aborts the run and still stops the pool`() {
        val executor = FakeExecutor { throw AssertionError("fatal") }
        assertThrows(AssertionError::class.java) { analysis(listOf(mutant()), executor).run() }
        assertTrue(writer.written.isEmpty())
        assertPoolStopped(executor)
    }

    @Test
    fun `skips classes outside the filters and methods that are excluded`() {
        val mutants =
            listOf(
                mutant("a.Foo", "keep"),
                mutant("a.Foo", "skip"),
                mutant("b.Bar", "keep"),
                mutant("a.Foo", "x"),
                mutant("a.Baz", "x"),
            )
        val generator = FakeGenerator(mutants)
        val filters =
            ClassFilters(
                excludes = listOf(Glob("b.*")),
                excludedMethods = setOf("skip", "a.Foo#x"),
            )
        val report = analysis(mutants, generator = generator, filters = filters).run()
        assertEquals(listOf(listOf("a.Foo", "a.Baz")), generator.discovered.toList())
        assertEquals(listOf(listOf("a.Foo", "b.Bar", "a.Baz")), generator.known.toList())
        assertEquals(listOf("a.Foo", "a.Baz"), report.results.map { it.mutant.className })
        assertEquals(listOf("keep", "x"), report.results.map { it.mutant.methodName })
    }

    @Test
    fun `applies the per-class mutant limit`() {
        val mutants = (0 until 3).map { mutant("a.Foo", ordinal = it) } + mutant("b.Bar")
        val report = analysis(mutants, filters = ClassFilters(maxMutantsPerClass = 2)).run()
        assertEquals(mutants.take(2) + mutants.last(), report.results.map { it.mutant })
    }

    @Test
    fun `reports how many eligible mutants the cap omitted`() {
        val mutants = (0 until 3).map { mutant("a.Foo", ordinal = it) }
        val executor = FakeExecutor { Outcome.Killed("t") }
        val report = analysis(mutants, executor, filters = ClassFilters(maxMutantsPerClass = 1)).run()
        assertEquals(1.0, report.score)
        assertEquals(1, report.results.size)
        assertEquals(2, report.omitted)
        assertEquals(report, writer.written.single())
    }

    @Test
    fun `counts omitted mutants over several classes but not excluded ones`() {
        val mutants =
            (0 until 3).map { mutant("a.Foo", ordinal = it) } +
                (0 until 2).map { mutant("b.Bar", ordinal = it) } +
                mutant("c.Baz", "skip") +
                mutant("d.Gone")
        val filters =
            ClassFilters(excludes = listOf(Glob("d.*")), excludedMethods = setOf("skip"), maxMutantsPerClass = 2)
        val report = analysis(mutants, filters = filters).run()
        assertEquals(4, report.results.size)
        assertEquals(1, report.omitted)
    }

    @Test
    fun `omits nothing when the cap is not reached`() {
        val mutants = (0 until 2).map { mutant("a.Foo", ordinal = it) }
        assertEquals(0, analysis(mutants, filters = ClassFilters(maxMutantsPerClass = 2)).run().omitted)
        assertEquals(0, analysis(mutants).run().omitted)
    }

    @Test
    fun `can run repeatedly and leaves no threads behind`() {
        val mutants = (0 until 6).map { mutant(ordinal = it) }
        val executor = FakeExecutor()
        val analysis = analysis(mutants, executor)
        val first = analysis.run()
        val second = analysis.run()
        assertEquals(first, second)
        assertEquals(2, writer.written.size)
        assertPoolStopped(executor)
    }

    @Test
    fun `works with a single worker`() {
        val mutants = (0 until 3).map { mutant(ordinal = it) }
        assertEquals(mutants, analysis(mutants, parallelism = 1).run().results.map { it.mutant })
    }

    @Test
    fun `writes the returned report once and reports progress per mutant`() {
        val mutants = (0 until 5).map { mutant(ordinal = it) }
        val progress = ConcurrentLinkedQueue<MutantResult>()
        val report = analysis(mutants).run(progress = progress::add)
        assertEquals(listOf(report), writer.written.toList())
        assertEquals(report.results.toSet(), progress.toSet())
        assertEquals(mutants.size, progress.size)
    }

    @Test
    fun `writes an empty report when there is nothing to mutate`() {
        val report = analysis(emptyList()).run()
        assertTrue(report.results.isEmpty())
        assertEquals(1, writer.written.size)
    }

    @Test
    fun `runs the baseline once with five times the configured timeout before the mutants`() {
        val executor = FakeExecutor()
        analysis((0 until 3).map { mutant(ordinal = it) }, executor).run()
        assertEquals(listOf(35.seconds), executor.baselines.toList())
    }

    @Test
    fun `each mutant gets the measured baseline time scaled by the factor plus the constant`() {
        val executor = FakeExecutor(baselineElapsed = 500.milliseconds)
        analysis((0 until 2).map { mutant(ordinal = it) }, executor).run()
        assertEquals(listOf(6.seconds, 6.seconds), executor.received.map { it.third })
    }

    @Test
    fun `the configured timeout caps the derived deadline`() {
        val executor = FakeExecutor(baselineElapsed = 3.seconds)
        analysis(listOf(mutant()), executor).run()
        assertEquals(listOf(7.seconds), executor.received.map { it.third })
    }

    @Test
    fun `a capped deadline is reported with the baseline time and the deadline it needed`() {
        val report = analysis(listOf(mutant()), FakeExecutor(baselineElapsed = 3.seconds)).run()
        assertEquals(DeadlineCap(7.seconds, 3.seconds, 16.seconds), report.deadlineCap)
    }

    @Test
    fun `a capped deadline is announced once, before any mutant runs`() {
        val events = ConcurrentLinkedQueue<String>()
        val executor =
            FakeExecutor(baselineElapsed = 3.seconds) {
                events += "mutant"
                Outcome.Survived
            }
        analysis((0 until 2).map { mutant(ordinal = it) }, executor).run(onDeadlineCap = { events += "cap $it" })
        assertEquals(
            listOf("cap ${DeadlineCap(7.seconds, 3.seconds, 16.seconds)}", "mutant", "mutant"),
            events.toList(),
        )
    }

    @Test
    fun `no cap is reported when the derived deadline fits within the timeout`() {
        assertNull(analysis(listOf(mutant()), FakeExecutor(baselineElapsed = 750.milliseconds)).run().deadlineCap)
        assertNull(analysis(emptyList()).run().deadlineCap)
    }

    @Test
    fun `a red baseline stops the run naming the failing test, before any mutant or report`() {
        val executor = FakeExecutor(baseline = { Outcome.Killed("a.FooTest.unrelated") })
        val error = assertThrows(IllegalStateException::class.java) { analysis(listOf(mutant()), executor).run() }
        assertTrue("a.FooTest.unrelated" in error.message.orEmpty(), error.message)
        assertTrue(executor.received.isEmpty())
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun `a red baseline names the cause of its first failure`() {
        val cause = "org.opentest4j.AssertionFailedError: red"
        val executor = FakeExecutor(baseline = { Outcome.Killed("a.FooTest.unrelated", cause) })
        val error = assertThrows(IllegalStateException::class.java) { analysis(listOf(mutant()), executor).run() }
        assertTrue("the first failure is a.FooTest.unrelated: $cause" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a baseline that times out or cannot load stops the run`() {
        listOf(Outcome.Timeout, Outcome.LoadError("VerifyError: x")).forEach { baseline ->
            val executor = FakeExecutor(baseline = { baseline })
            assertThrows(IllegalStateException::class.java) { analysis(listOf(mutant()), executor).run() }
            assertTrue(executor.received.isEmpty())
        }
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun `a baseline that throws is reported as such and no mutant is scored`() {
        val executor = FakeExecutor(baseline = { throw IllegalStateException("Forked test JVM failed (exit code 1)") })
        val error = assertThrows(IllegalStateException::class.java) { analysis(listOf(mutant()), executor).run() }
        assertTrue("no mutant was scored" in error.message.orEmpty() && "exit code 1" in error.message.orEmpty())
        assertTrue(executor.received.isEmpty())
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun `an executor that cannot continue stops the analysis with its message and writes no report`() {
        val mutants = (0 until 4).map { mutant(ordinal = it) }
        val executor =
            FakeExecutor { if (it.id.ordinal == 2) throw TestExecutionStopped("stalled") else Outcome.Survived }
        val error = assertThrows(TestExecutionStopped::class.java) { analysis(mutants, executor).run() }
        assertEquals("stalled", error.message)
        assertTrue(writer.written.isEmpty())
        assertPoolStopped(executor)
    }

    @Test
    fun `a timed out baseline names timeoutMs`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                analysis(listOf(mutant()), FakeExecutor(baseline = { Outcome.Timeout })).run()
            }
        assertTrue("timeoutMs" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a baseline without tests does not stop the run`() {
        val report = analysis(listOf(mutant()), FakeExecutor(baseline = { Outcome.NoTests })).run()
        assertEquals(1, report.results.size)
    }

    @Test
    fun `an empty run skips the baseline`() {
        val executor = FakeExecutor(baseline = { Outcome.Killed("red") })
        analysis(emptyList(), executor).run()
        assertTrue(executor.baselines.isEmpty())
        assertEquals(1, writer.written.size)
    }

    private fun assertPoolStopped(executor: FakeExecutor) =
        executor.threads.forEach {
            it.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertFalse(it.isAlive, "worker ${it.name} still alive")
        }
}
