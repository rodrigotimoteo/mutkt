package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val WAIT_SECONDS = 10L

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
        val report = analysis(mutants).run(progress::add)
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

    private fun assertPoolStopped(executor: FakeExecutor) =
        executor.threads.forEach {
            it.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertFalse(it.isAlive, "worker ${it.name} still alive")
        }
}
