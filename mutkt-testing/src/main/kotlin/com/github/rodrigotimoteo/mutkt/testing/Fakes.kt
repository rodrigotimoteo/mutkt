package com.github.rodrigotimoteo.mutkt.testing

import com.github.rodrigotimoteo.mutkt.application.port.Baseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TestId
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
    override fun discover(
        classes: List<ClassFile>,
        targets: List<ClassFile>,
    ): List<Mutant> = mutants

    override fun materialize(mutant: Mutant): ByteArray = bytes[mutant] ?: error("No bytes registered for $mutant")
}

/**
 * Returns the scripted outcome per mutant ([default] otherwise) and records each call's mutant, bytes and deadline in
 * [runs] and its test classes (null: all) in [selections], index for index;
 * [baseline] answers the baseline run in [baselineElapsed]; its deadlines are recorded in [baselines].
 */
class FakeTestExecutor(
    private val outcomes: Map<Mutant, Outcome> = emptyMap(),
    private val default: Outcome = Outcome.Survived,
    private val baseline: Outcome = Outcome.Survived,
    private val baselineElapsed: Duration = Duration.ZERO,
) : TestExecutor {
    val runs = mutableListOf<Triple<Mutant, ByteArray, Duration>>()
    val selections = mutableListOf<List<TestId>?>()
    val baselines = mutableListOf<Duration>()

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        tests: List<TestId>?,
    ): Outcome {
        runs += Triple(mutant, bytes, deadline)
        selections += tests
        return outcomes[mutant] ?: default
    }

    override fun baseline(
        deadline: Duration,
        instrumented: Instrumented?,
    ): Baseline {
        baselines += deadline
        return Baseline(baseline, baselineElapsed)
    }
}

class FakeReportWriter : ReportWriter {
    val reports = mutableListOf<MutationReport>()

    override fun write(report: MutationReport) {
        reports += report
    }
}

/** Looks up lines by (className, line), whatever the source file; the source text has empty lines in the gaps. */
class FakeSourceLookup(
    private val lines: Map<Pair<String, Int>, String> = emptyMap(),
) : SourceLookup {
    override fun lineText(
        className: String,
        sourceFile: String?,
        line: Int,
    ): String? = lines[className to line]

    override fun sourceText(
        className: String,
        sourceFile: String?,
    ): String? =
        lines.keys
            .filter { it.first == className }
            .maxOfOrNull { it.second }
            ?.let { last -> (1..last).joinToString("\n") { lines[className to it].orEmpty() } }
}
