package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.Survivor
import com.github.rodrigotimoteo.mutkt.application.SurvivorBaseline
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Which survivors a baseline accepts after an edit, on real compiler output. */
class SurvivorIdentityTest {
    private val gate =
        """
        package demo

        class Gate {
            fun check(age: Int): Boolean {
                return age >= 18
            }
        }
        """.trimIndent()

    // Every mutant survives, as if no test checked the boundaries.
    private fun survivors(source: String): List<Survivor> {
        val classes = compileKotlin(source)
        val files = classes.map { (name, bytes) -> ClassFile(name, bytes) }
        val mutants = AsmMutantGenerator(setOf("conditionals-boundary"), {}) { null }.discover(files, files)
        val report = MutationReport(mutants.map { MutantResult(it, MutantStatus.SURVIVED) })
        return survivorsOf(report) { classes.getValue(it) }
    }

    private val accepted = SurvivorBaseline(survivors(gate).toSet())
    private val acceptedId = "conditionals-boundary:demo.Gate#check(I)Z:0"

    @Test
    fun `the accepted survivor is the boundary at ordinal 0`() =
        assertEquals(listOf(acceptedId), accepted.survivors.map { it.id })

    @Test
    fun `unchanged code is accepted`() = assertEquals(emptyList<Survivor>(), accepted.unaccepted(survivors(gate)))

    @Test
    fun `an edit that only moves lines is accepted`() {
        val moved = gate.replace("class Gate {", "// moved down\n\n\nclass Gate {")

        assertEquals(emptyList<Survivor>(), accepted.unaccepted(survivors(moved)))
    }

    @Test
    fun `a new site earlier in the method flags the survivor now at the accepted ordinal`() {
        val edited = gate.replace("return age >= 18", "if (age > 150) return false\n        return age >= 18")
        val now = survivors(edited)

        // The new site takes ordinal 0 and the accepted one moves to 1: matching ids alone would accept the wrong one.
        assertEquals(listOf(acceptedId, "conditionals-boundary:demo.Gate#check(I)Z:1"), now.map { it.id })
        assertNotEquals(accepted.survivors.single().method, now.first().method)
        assertEquals(now, accepted.unaccepted(now))
    }
}
