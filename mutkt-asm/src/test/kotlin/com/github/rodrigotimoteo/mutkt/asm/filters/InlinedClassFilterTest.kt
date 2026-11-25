package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.ToyOperator
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.asm.toBytes
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InlinedClassFilterTest {
    private val classes =
        compileKotlin(
            """
            interface Cb { fun call(): Int }
            inline fun make(crossinline f: () -> Int) = object : Cb { override fun call() = f() + 1 }
            fun made(n: Int): Int = make { n + 2 }.call()
            fun sorted(l: List<String>) = l.sortedBy { it.length + 1 }
            """.trimIndent(),
        )
    private val copy = "SourceKt\$made\$\$inlined\$make\$1"

    @Test
    fun `the copy of the object keeps the lambda body and drops the rest`() {
        // f() + 1 is the copy of the original object; n + 2 is the lambda the user wrote.
        assertEquals(listOf("IADD"), InlinedClassFilter.noiseIn(classes, copy, "call").filter { it == "IADD" })
        assertEquals(1, discover(classes.getValue(copy), ToyOperator).size)
    }

    @Test
    fun `the original object of the inline function is untouched`() {
        assertEquals(emptyList<String>(), InlinedClassFilter.noiseIn(classes, "SourceKt\$make\$1", "call"))
        assertEquals(1, discover(classes.getValue("SourceKt\$make\$1"), ToyOperator).size)
    }

    @Test
    fun `a standard library copy keeps the user lambda only`() {
        val sortedCopy = "SourceKt\$sorted\$\$inlined\$sortedBy\$1"

        assertEquals(
            true,
            "INVOKESTATIC ComparisonsKt.compareValues" in InlinedClassFilter.noiseIn(classes, sortedCopy, "compare"),
        )
        // it.length + 1 is the lambda, twice.
        assertEquals(2, discover(classes.getValue(sortedCopy), ToyOperator).size)
    }

    @Test
    fun `other classes and methods are never noise`() {
        assertEquals(emptyList<String>(), InlinedClassFilter.noiseIn(classes, "SourceKt", "made"))
    }

    @Test
    fun `without debug info nothing is recognised`() {
        val node = parse(classes.getValue(copy)).also { n -> n.methods.forEach { it.localVariables = null } }

        assertEquals(2, discover(node.toBytes(), ToyOperator).size)
    }
}
