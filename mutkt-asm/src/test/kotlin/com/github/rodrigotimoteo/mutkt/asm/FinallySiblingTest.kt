package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.operators.VoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// close() is written twice by hand in sibling branches of one method; only one of them is inside a finally.
private val SOURCE =
    """
    var closes = 0
    fun close() { closes++ }
    fun work(fail: Boolean) { if (fail) throw IllegalStateException("boom") }

    fun f(side: Boolean, fail: Boolean) {
        if (side) {
            close() // sibling
        } else {
            try {
                work(fail)
            } finally {
                close() // finally
            }
        }
    }
    fun g(side: Boolean, fail: Boolean) {
        if (side) {
            try {
                work(fail)
            } finally {
                close() // g finally
            }
        } else {
            close() // g sibling
        }
    }
    fun probe(side: Boolean, fail: Boolean): Int {
        closes = 0
        try { f(side, fail) } catch (e: IllegalStateException) { }
        return closes
    }
    fun probeG(side: Boolean, fail: Boolean): Int {
        closes = 0
        try { g(side, fail) } catch (e: IllegalStateException) { }
        return closes
    }
    """.trimIndent()

class FinallySiblingTest {
    private val classes = compileKotlin(SOURCE)
    private val hierarchy: Hierarchy = { type -> classes[type.replace('/', '.')] }
    private val bytes = classes.getValue("SourceKt")

    private fun lineOf(text: String) = SOURCE.lines().indexOfFirst { text in it } + 1

    private fun closeSite(text: String) = discover(bytes, VoidMethodCallsOperator).single { it.line == lineOf(text) }

    private fun probes(
        mutant: Map<String, ByteArray>,
        probe: String = "probe",
    ) = listOf(true to false, false to false, true to true, false to true).map { (side, fail) ->
        loadAndInvoke(mutant, "SourceKt", probe, side, fail)
    }

    private fun mutated(text: String) =
        classes + ("SourceKt" to mutate(bytes, VoidMethodCallsOperator, closeSite(text), hierarchy))

    @Test
    fun `the sibling close and the finally close are two mutants`() {
        assertEquals(
            listOf("close() // sibling", "work(fail)", "close() // finally").map(::lineOf),
            discover(bytes, VoidMethodCallsOperator).filter { it.methodName == "f" }.map { it.line },
        )
    }

    @Test
    fun `removing the sibling close keeps both copies of the finally close`() {
        assertEquals(listOf(1, 1, 1, 1), probes(classes))
        assertEquals(listOf(0, 1, 0, 1), probes(mutated("// sibling")))
    }

    @Test
    fun `removing the finally close keeps the sibling close`() {
        assertEquals(listOf(1, 0, 1, 0), probes(mutated("// finally")))
    }

    @Test
    fun `a sibling branch written after the try is separate as well`() {
        assertEquals(listOf(1, 1, 1, 1), probes(classes, "probeG"))
        assertEquals(listOf(0, 1, 0, 1), probes(mutated("// g finally"), "probeG"))
        assertEquals(listOf(1, 0, 1, 0), probes(mutated("// g sibling"), "probeG"))
    }
}
