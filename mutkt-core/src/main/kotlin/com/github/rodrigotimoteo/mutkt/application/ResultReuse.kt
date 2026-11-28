package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus

/** A mutant's [result] and the history [key] it was evaluated under, null when it got none. */
internal data class Keyed(
    val result: MutantResult,
    val key: String?,
)

/**
 * One run's view of the history: keys each mutant on what its tests can observe and answers the [stored] entry when
 * that key and the run's fingerprint still match. Reach comes from the baseline, so a mutant that sends a test into a
 * class the baseline never reached is keyed without that class. Classes no test's reach can show (unprobed, or entered
 * outside any test class) are in every key.
 */
internal class ResultReuse(
    private val stored: History?,
    settings: HistoryReuse,
    mainClasses: List<ClassFile>,
    coverage: Coverage?,
    unprobed: Set<String>,
) {
    private val fingerprint = settings.fingerprint.digest()
    private val supertypes = settings.testSupertypes
    private val main = mainClasses.associate { it.name to sha256(it.bytes) }
    private val tests = settings.testClasses.classes().associate { it.name to sha256(it.bytes) }
    private val reach = coverage?.tests?.associate { it.test.className to it.reached }.orEmpty()
    private val reachedByAll = unprobed + coverage?.globalReach.orEmpty()

    // A whole-suite mutant (null [covering]) can be reached by any test, so every class is in its key.
    fun key(
        mutant: Mutant,
        bytes: ByteArray,
        covering: List<TestId>?,
    ): String {
        if (covering == null) return mutantKey(mutant.id, bytes, tests, main)
        val reached = covering.flatMap { reach[it.className].orEmpty() }.toSet() + reachedByAll
        val owners = covering.flatMap { testAndBases(it.className) }.toSet()
        val ownTests = tests.filterKeys { name -> name in owners || owners.any { name.startsWith("$it\$") } }
        return mutantKey(mutant.id, bytes, ownTests, main.filterKeys { it in reached })
    }

    fun reusable(
        mutant: MutantId,
        key: String,
    ): HistoryEntry? = stored?.reusable(fingerprint, mutant, key)

    // This run's reusable results under their keys, plus the stored entries of mutants it did not evaluate (a narrower
    // run), which stay valid while the fingerprint matches.
    fun next(evaluated: List<Keyed>): History {
        val fresh =
            evaluated.mapNotNull { (result, key) ->
                key?.takeIf { result.status in KEPT }?.let {
                    HistoryEntry(result.mutant.id.toString(), it, result.status, result.killedBy, result.killKind)
                }
            }
        val previous = stored?.takeIf { it.fingerprint == fingerprint } ?: return History(fingerprint, fresh)
        val seen = evaluated.mapTo(mutableSetOf()) { "${it.result.mutant.id}" }
        return History(fingerprint, fresh + previous.entries.filter { it.mutant !in seen })
    }

    // The test class and its superclasses and interfaces that are themselves in the test sources.
    private fun testAndBases(name: String): Set<String> {
        val found = mutableSetOf<String>()
        val pending = ArrayDeque(listOf(name))
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (found.add(next)) pending += supertypes[next].orEmpty().filter { it in tests }
        }
        return found
    }
}

// ERROR may be transient and NO_COVERAGE comes fresh from every baseline.
private val KEPT = setOf(MutantStatus.KILLED, MutantStatus.SURVIVED, MutantStatus.TIMEOUT)
