package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The compiler copies a finally body to the normal and the exception path, so a type test written once appears twice.
private val SOURCE =
    """
    var seen = 0
    fun record(b: Boolean) { seen = if (b) 1 else 0 }
    fun work(fail: Boolean) { if (fail) throw IllegalStateException("boom") }

    fun lone(x: Any, fail: Boolean) {
        try { work(fail) } finally { record(x is String) }
    }
    fun branches(x: Any, fail: Boolean) {
        try { work(fail) } finally { seen = when (x) { is String -> 1; is Int -> 2; else -> 3 } }
    }
    fun mixed(x: Any, fail: Boolean): Int {
        val inner = try { work(fail); 1 } finally { record(x is String) }
        return if (x is Int) inner else 0
    }
    fun other(x: Any): Int = when (x) { is String -> 1; is Int -> 2; else -> 3 }

    fun probe(name: String, x: Any, fail: Boolean): Int {
        seen = -1
        try {
            when (name) {
                "lone" -> lone(x, fail)
                "mixed" -> mixed(x, fail)
                else -> branches(x, fail)
            }
        } catch (e: IllegalStateException) { }
        return seen
    }
    """.trimIndent()

class SealedWhenFinallyTest {
    private val classes = compileKotlin(SOURCE)
    private val bytes = classes.getValue("SourceKt")
    private val hierarchy: Hierarchy = { type -> classes[type.replace('/', '.')] }

    private fun sites(method: String) = discover(bytes, SealedWhenOperator).filter { it.methodName == method }

    private fun probe(
        mutant: Map<String, ByteArray>,
        name: String,
    ) = listOf("s" to false, "s" to true, 1 to false, 1 to true, 1.5 to false, 1.5 to true).map { (x, fail) ->
        loadAndInvoke(mutant, "SourceKt", "probe", name, x, fail)
    }

    private fun generated() =
        classes.map { (name, bytes) -> ClassFile(name, bytes) }.let {
            AsmMutantGenerator(setOf("sealed-when")) { null }.discover(it, it)
        }

    @Test
    fun `a type test written once in a finally is a lone test with no site`() {
        assertEquals(emptyList<Any>(), sites("lone"))
        assertEquals(emptyList<String>(), generated().map { it.methodName }.filter { it == "lone" })
    }

    @Test
    fun `discovery still finds the sites of other methods`() {
        assertEquals(2, sites("other").size)
    }

    @Test
    fun `a test of a local in a finally and one outside it are two mutants, the finally one changes every copy`() {
        val found = sites("mixed")
        val finallyTest = found.single { it.description.contains("String") }
        val mutant = classes + ("SourceKt" to mutate(bytes, SealedWhenOperator, finallyTest, hierarchy))

        assertEquals(2, found.size)
        assertEquals(listOf(1, 1, 0, 0, 0, 0), probe(classes, "mixed"))
        assertEquals(listOf(0, 0, 0, 0, 0, 0), probe(mutant, "mixed"))
    }

    @Test
    fun `a when in a finally is one mutant that changes every copy`() {
        val found = sites("branches")
        val mutant = classes + ("SourceKt" to mutate(bytes, SealedWhenOperator, found.first(), hierarchy))

        assertEquals(2, found.size)
        assertEquals(listOf(1, 1, 2, 2, 3, 3), probe(classes, "branches"))
        assertEquals(listOf(3, 3, 2, 2, 3, 3), probe(mutant, "branches"))
    }
}
