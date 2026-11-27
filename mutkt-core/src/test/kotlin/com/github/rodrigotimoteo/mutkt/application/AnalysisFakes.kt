package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
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

// The default baseline time is longer than any configured timeout, so mutants get the timeout unless a test sets it.
internal class FakeExecutor(
    private val baseline: () -> Outcome = { Outcome.Survived },
    private val baselineElapsed: Duration = 1.minutes,
    private val behavior: (Mutant) -> Outcome = { Outcome.Survived },
) : TestExecutor {
    val baselines = ConcurrentLinkedQueue<Duration>()
    val threads = ConcurrentLinkedQueue<Thread>()
    val received = ConcurrentLinkedQueue<Triple<Mutant, String, Duration>>()

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        tests: List<TestId>?,
    ): Outcome {
        threads += Thread.currentThread()
        received += Triple(mutant, String(bytes), deadline)
        return behavior(mutant)
    }

    override fun baseline(
        deadline: Duration,
        instrumented: Instrumented?,
    ): Baseline {
        baselines += deadline
        return Baseline(baseline(), baselineElapsed)
    }
}

internal class RecordingWriter : ReportWriter {
    val written = ConcurrentLinkedQueue<MutationReport>()

    override fun write(report: MutationReport) {
        written += report
    }
}
