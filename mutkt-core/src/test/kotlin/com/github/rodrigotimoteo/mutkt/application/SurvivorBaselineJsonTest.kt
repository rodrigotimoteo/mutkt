package com.github.rodrigotimoteo.mutkt.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurvivorBaselineJsonTest {
    private val baseline =
        SurvivorBaseline(setOf(Survivor("negate:a.Foo#run()I:1", "m1"), Survivor("negate:a.Foo#run()I:0", "m0")))

    private val tree =
        mapOf(
            "version" to 1,
            "survivors" to
                listOf(
                    mapOf("id" to "negate:a.Foo#run()I:0", "method" to "m0"),
                    mapOf("id" to "negate:a.Foo#run()I:1", "method" to "m1"),
                ),
        )

    @Test
    fun `writes the versioned tree sorted by id, so the committed file diffs cleanly`() =
        assertEquals(tree, baseline.toJson())

    @Test
    fun `reads the tree back, whatever number type the parser gives`() {
        assertEquals(baseline, survivorBaselineFromJson(tree))
        assertEquals(baseline, survivorBaselineFromJson(tree + ("version" to 1.0)))
    }

    @Test
    fun `fails on another format version or any other shape`() {
        listOf(
            tree + ("version" to 2),
            tree - "version",
            tree - "survivors",
            tree + ("survivors" to listOf("x")),
            tree + ("survivors" to listOf(mapOf("id" to "a"))),
        ).forEach { broken ->
            val error = assertThrows(IllegalArgumentException::class.java) { survivorBaselineFromJson(broken) }
            assertTrue(error.message!!.startsWith("Corrupt survivor baseline"), error.message)
        }
    }
}
