package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.CoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val OPERATOR = OperatorId("op")

internal fun mutant(
    className: String = "a.Foo",
    method: String = "m",
    ordinal: Int = 0,
) = Mutant(MutantId(OPERATOR, className, method, "()V", ordinal), OPERATOR, className, method, "()V", 1, "d")

internal fun classFile(name: String) = ClassFile(name, name.toByteArray())

internal class FakeClassSource(
    private val classes: List<ClassFile>,
) : ClassSource {
    override fun classes() = classes
}

internal class FakeGenerator(
    private val mutants: List<Mutant>,
    private val failing: Set<Mutant> = emptySet(),
    private val failure: Throwable = IllegalStateException("cannot materialize"),
) : MutantGenerator {
    val discovered = ConcurrentLinkedQueue<List<String>>()
    val known = ConcurrentLinkedQueue<List<String>>()

    override fun discover(
        classes: List<ClassFile>,
        targets: List<ClassFile>,
    ): List<Mutant> {
        known += classes.map { it.name }
        discovered += targets.map { it.name }
        return mutants.filter { m -> targets.any { it.name == m.className } }
    }

    override fun materialize(mutant: Mutant): ByteArray {
        if (mutant in failing) throw failure
        return mutant.id.toString().toByteArray()
    }
}

/** Returns [probes] as the instrumented classes' map and records what it was asked to instrument. */
internal class FakeInstrumenter(
    private val probes: Map<MutantId, Probe> = emptyMap(),
) : CoverageInstrumenter {
    val calls = ConcurrentLinkedQueue<Pair<List<String>, List<Mutant>>>()
    val made = ConcurrentLinkedQueue<Instrumented>()

    override fun instrument(
        classes: List<ClassFile>,
        mutants: List<Mutant>,
    ): Instrumented {
        calls += classes.map { it.name } to mutants
        return Instrumented(emptyList(), probes).also { made += it }
    }
}

// The default baseline time is longer than any configured timeout, so mutants get the timeout unless a test sets it.
// A baseline on instrumented classes answers [instrumentedBaseline] (else [baseline]) with [coverage].
internal class FakeExecutor(
    private val baseline: () -> Outcome = { Outcome.Survived },
    private val baselineElapsed: Duration = 1.minutes,
    private val coverage: Coverage? = null,
    private val instrumentedBaseline: () -> Outcome = baseline,
    private val behavior: (Mutant) -> Outcome = { Outcome.Survived },
) : TestExecutor {
    val baselines = ConcurrentLinkedQueue<Duration>()
    val baselineInputs: MutableList<Instrumented?> = Collections.synchronizedList(mutableListOf())
    val threads = ConcurrentLinkedQueue<Thread>()
    val received = ConcurrentLinkedQueue<Triple<Mutant, String, Duration>>()
    val selections = ConcurrentLinkedQueue<Pair<Mutant, List<TestId>?>>()

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        tests: List<TestId>?,
    ): Outcome {
        threads += Thread.currentThread()
        received += Triple(mutant, String(bytes), deadline)
        selections += mutant to tests
        return behavior(mutant)
    }

    override fun baseline(
        deadline: Duration,
        instrumented: Instrumented?,
    ): Baseline {
        baselines += deadline
        baselineInputs += instrumented
        if (instrumented == null) return Baseline(baseline(), baselineElapsed)
        return Baseline(instrumentedBaseline(), baselineElapsed, coverage)
    }
}

internal class RecordingWriter : ReportWriter {
    val written = ConcurrentLinkedQueue<MutationReport>()

    override fun write(report: MutationReport) {
        written += report
    }
}
