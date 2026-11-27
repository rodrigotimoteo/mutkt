package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The test classes to run against one mutant, in order, within [deadline]: null runs the whole suite, empty none. */
internal data class Selection(
    val tests: List<TestId>?,
    val deadline: Duration,
)

/**
 * Picks each mutant's tests from the probes the baseline reached on the instrumented classes; without [coverage]
 * every mutant runs the whole suite within [suiteDeadline].
 */
internal class CoverageMap(
    private val probes: Map<MutantId, Probe>,
    private val coverage: Coverage?,
    private val timeout: Duration,
    private val suiteDeadline: Duration,
) {
    // In baseline order: an order-dependent suite then sees its classes as in a full run, and killedBy is stable.
    private val reaching = HashMap<Probe, MutableList<TestCoverage>>()

    // The first test class paid the class loading and first-use library set-up that each run's fresh loader pays
    // again.
    private val warmUp = coverage?.tests?.firstOrNull()?.elapsed ?: Duration.ZERO

    init {
        for (test in coverage?.tests.orEmpty()) {
            for (probe in test.hits) reaching.getOrPut(probe) { mutableListOf() } += test
        }
    }

    fun selection(mutant: Mutant): Selection {
        val probe = telling(mutant) ?: return Selection(null, suiteDeadline)
        val tests = reaching[probe].orEmpty()
        val elapsed = tests.fold(Duration.ZERO) { sum, test -> sum + test.elapsed }
        return Selection(tests.map { it.test }, minOf(timeout, neededDeadline(elapsed) + warmUp))
    }

    // The mutant's probe when its hits name the tests that depend on the site. One hit outside any test class, or in
    // a class initialiser (which runs once, under whichever test class loads the class first), does not.
    private fun telling(mutant: Mutant): Probe? {
        val global = coverage?.global ?: return null
        return probes[mutant.id]?.takeUnless { it in global || mutant.methodName == CLASS_INIT }
    }
}

/** The deadline tests that ran for [elapsed] on the original classes need against a mutant. */
internal fun neededDeadline(elapsed: Duration): Duration = elapsed * MUTANT_DEADLINE_FACTOR + MUTANT_DEADLINE_CONSTANT

private const val CLASS_INIT = "<clinit>"

// The baseline runs alone, mutants run `parallelism` at a time: on the dogfood asm suite (8 threads, 11 cores) a
// mutant run took up to 2.9 times the baseline, so a lower factor turns slow kills into timeouts.
private const val MUTANT_DEADLINE_FACTOR = 4

// Absorbs JIT, GC and scheduling noise, which dominates a suite that runs in milliseconds.
private val MUTANT_DEADLINE_CONSTANT = 4.seconds
