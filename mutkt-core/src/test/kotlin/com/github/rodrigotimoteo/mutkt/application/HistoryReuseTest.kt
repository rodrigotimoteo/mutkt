package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

// Two mutants in different classes, each covered by its own test class: a change to one side must not touch the other.
class HistoryReuseTest {
    private val run =
        RunFingerprint(
            "0.5.0",
            emptySet(),
            setOf("junit-jupiter"),
            emptySet(),
            emptySet(),
            60.seconds,
            emptyList(),
            "21",
            "/p",
            emptyMap(),
            emptyMap(),
        )
    private val a = MutantId(OperatorId("negate"), "a.A", "run", "()I", 0)
    private val b = MutantId(OperatorId("negate"), "a.B", "run", "()I", 0)
    private val bytes = mapOf(a to byteArrayOf(1), b to byteArrayOf(2))
    private val tests = mapOf(a to mapOf("a.ATest" to "ta"), b to mapOf("a.BTest" to "tb"))
    private val reached = mapOf(a to mapOf("a.A" to "ma"), b to mapOf("a.B" to "mb"))

    private fun key(
        id: MutantId,
        bytes: Map<MutantId, ByteArray> = this.bytes,
        tests: Map<MutantId, Map<String, String>> = this.tests,
        reached: Map<MutantId, Map<String, String>> = this.reached,
    ) = mutantKey(id, bytes.getValue(id), tests.getValue(id), reached.getValue(id))

    private val history =
        History(
            run.digest(),
            listOf(
                HistoryEntry(a.toString(), key(a), MutantStatus.KILLED, "a.ATest"),
                HistoryEntry(b.toString(), key(b), MutantStatus.SURVIVED),
            ),
        )

    // Which of a and b the history still answers for, given the current run's fingerprint and keys.
    private fun reused(
        fingerprint: RunFingerprint = run,
        keys: (MutantId) -> String = { key(it) },
    ) = listOf(a, b).filter { history.reusable(fingerprint.digest(), it, keys(it)) != null }

    @Test
    fun `an unchanged run reuses every entry with its status`() {
        assertEquals(listOf(a, b), reused())
        assertEquals(
            HistoryEntry(a.toString(), key(a), MutantStatus.KILLED, "a.ATest"),
            history.reusable(run.digest(), a, key(a)),
        )
    }

    @Test
    fun `a changed fingerprint drops every entry`() =
        assertEquals(emptyList<MutantId>(), reused(fingerprint = run.copy(java = "17")))

    @Test
    fun `changed mutant bytes drop only that mutant`() =
        assertEquals(listOf(b), reused { key(it, bytes = bytes + (a to byteArrayOf(9))) })

    @Test
    fun `a changed covering test class drops only the mutants it covers`() =
        assertEquals(listOf(a), reused { key(it, tests = tests + (b to mapOf("a.BTest" to "tb2"))) })

    @Test
    fun `a changed reached main class drops only the mutants whose tests reach it`() =
        assertEquals(listOf(b), reused { key(it, reached = reached + (a to mapOf("a.A" to "ma2"))) })

    @Test
    fun `an unknown mutant is not reused`() = assertNull(history.reusable(run.digest(), a.copy(ordinal = 1), key(a)))

    @Test
    fun `only killed, survived and timed-out results are kept`() {
        listOf(MutantStatus.ERROR, MutantStatus.NO_COVERAGE).forEach { status ->
            assertThrows(IllegalArgumentException::class.java) { HistoryEntry(a.toString(), "k", status) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryEntry(a.toString(), "k", MutantStatus.SURVIVED, killedBy = "a.ATest")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryEntry(a.toString(), "k", MutantStatus.SURVIVED, killKind = KillKind.EXCEPTION)
        }
    }

    @Test
    fun `a mutant appears once`() {
        val entry = HistoryEntry(a.toString(), "k", MutantStatus.TIMEOUT)
        assertThrows(IllegalArgumentException::class.java) { History("f", listOf(entry, entry.copy(key = "k2"))) }
    }
}
