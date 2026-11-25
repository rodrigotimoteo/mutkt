package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration

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

    override fun discover(classes: List<ClassFile>): List<Mutant> {
        discovered += classes.map { it.name }
        return mutants.filter { m -> classes.any { it.name == m.className } }
    }

    override fun materialize(mutant: Mutant): ByteArray {
        if (mutant in failing) throw failure
        return mutant.id.toString().toByteArray()
    }
}

internal class FakeExecutor(
    private val baseline: () -> Outcome = { Outcome.Survived },
    private val behavior: (Mutant) -> Outcome = { Outcome.Survived },
) : TestExecutor {
    val baselines = ConcurrentLinkedQueue<Duration>()
    val threads = ConcurrentLinkedQueue<Thread>()
    val received = ConcurrentLinkedQueue<Triple<Mutant, String, Duration>>()

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome {
        threads += Thread.currentThread()
        received += Triple(mutant, String(bytes), deadline)
        return behavior(mutant)
    }

    override fun baseline(deadline: Duration): Outcome {
        baselines += deadline
        return baseline()
    }
}

internal class RecordingWriter : ReportWriter {
    val written = ConcurrentLinkedQueue<MutationReport>()

    override fun write(report: MutationReport) {
        written += report
    }
}
