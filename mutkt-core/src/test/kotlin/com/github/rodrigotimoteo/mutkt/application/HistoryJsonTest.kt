package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryJsonTest {
    private val history =
        History(
            "f0",
            listOf(
                HistoryEntry("negate:a.Foo#run()I:0", "k0", MutantStatus.KILLED, "a.FooTest"),
                HistoryEntry("negate:a.Foo#run()I:1", "k1", MutantStatus.TIMEOUT),
            ),
        )

    private val tree =
        mapOf(
            "version" to 1,
            "fingerprint" to "f0",
            "mutants" to
                listOf(
                    mapOf(
                        "id" to "negate:a.Foo#run()I:0",
                        "key" to "k0",
                        "status" to "KILLED",
                        "killedBy" to "a.FooTest",
                    ),
                    mapOf("id" to "negate:a.Foo#run()I:1", "key" to "k1", "status" to "TIMEOUT"),
                ),
        )

    @Test
    fun `writes the versioned tree`() = assertEquals(tree, history.toJson())

    @Test
    fun `reads the tree back, whatever number type the parser gives`() {
        assertEquals(history, historyFromJson(tree))
        assertEquals(history, historyFromJson(tree + ("version" to 1L)))
        assertEquals(history, historyFromJson(tree + ("version" to 1.0)))
    }

    @Test
    fun `ignores a history of another format version`() = assertNull(historyFromJson(tree + ("version" to 2)))

    @Test
    fun `fails on any other shape`() {
        val entry = (tree.getValue("mutants") as List<*>)[1] as Map<*, *>
        listOf(
            tree - "version",
            tree + ("version" to "1"),
            tree + ("version" to 1.9),
            tree - "fingerprint",
            tree + ("mutants" to "none"),
            tree + ("mutants" to listOf("x")),
            tree + ("mutants" to listOf(entry - "key")),
            tree + ("mutants" to listOf(entry + ("status" to "ERROR"))),
            tree + ("mutants" to listOf(entry + ("status" to "GONE"))),
            tree + ("mutants" to listOf(entry + ("killedBy" to 3))),
        ).forEach { bad ->
            val error = assertThrows(IllegalArgumentException::class.java, { historyFromJson(bad) }, bad.toString())
            assertTrue(error.message!!.startsWith("Corrupt history: "), error.message)
        }
    }
}
