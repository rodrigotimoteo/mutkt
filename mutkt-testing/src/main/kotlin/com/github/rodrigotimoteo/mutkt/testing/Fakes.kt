package com.github.rodrigotimoteo.mutkt.testing

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import kotlin.time.Duration

class FakeClassSource(
    private val classes: List<ClassFile> = emptyList(),
) : ClassSource {
    override fun classes(): List<ClassFile> = classes
}

/** Discovers [mutants] whatever the input; materializes the bytes registered per mutant and fails for any other. */
class FakeMutantGenerator(
    private val mutants: List<Mutant> = emptyList(),
    private val bytes: Map<Mutant, ByteArray> = emptyMap(),
) : MutantGenerator {
    override fun discover(classes: List<ClassFile>): List<Mutant> = mutants

    override fun materialize(mutant: Mutant): ByteArray = bytes[mutant] ?: error("No bytes registered for $mutant")
}

/** Returns the scripted outcome per mutant ([default] otherwise) and records each call's mutant, bytes and deadline. */
class FakeTestExecutor(
    private val outcomes: Map<Mutant, Outcome> = emptyMap(),
    private val default: Outcome = Outcome.Survived,
) : TestExecutor {
    val runs = mutableListOf<Triple<Mutant, ByteArray, Duration>>()

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome {
        runs += Triple(mutant, bytes, deadline)
        return outcomes[mutant] ?: default
    }
}

class FakeReportWriter : ReportWriter {
    val reports = mutableListOf<MutationReport>()

    override fun write(report: MutationReport) {
        reports += report
    }
}

/** Looks up lines by the key (className, line). */
class FakeSourceLookup(
    private val lines: Map<Pair<String, Int>, String> = emptyMap(),
) : SourceLookup {
    override fun lineText(
        className: String,
        line: Int,
    ): String? = lines[className to line]
}
