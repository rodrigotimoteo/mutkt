package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun yes(): Boolean = true
    fun no(): Boolean = false
    fun local(a: Int): Boolean {
        val r = a > 0
        return r
    }
    fun expr(a: Int): Boolean = a > 0
    fun call(a: String): Boolean = a.isEmpty()
    fun twice(a: Int): Boolean {
        if (a < 0) return false
        return a > 5
    }
    fun over(a: Int): Boolean = a > 0
    fun over(a: String): Boolean = a.isEmpty()
    fun overInt(): Boolean = over(1)
    fun overString(): Boolean = over("")
    fun wide(a: Long, d: Double): Boolean = a > d
    fun boxed(a: Int): Boolean? = a > 0
    fun count(a: Int): Int = a
    fun unit(a: Int) { println(a) }
    suspend fun suspending(a: Int): Boolean = a > 0

    class Flags(val on: Boolean) {
        fun isOn(): Boolean = on
        val hasOn: Boolean get() = on
    }
    """.trimIndent()

class BooleanReturnsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(
        op: Operator,
        method: String,
    ) = discover(file, op).filter { it.methodName == method }

    private fun mutant(
        op: Operator,
        method: String,
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found =
            sites(op, method).single {
                it.ordinal == ordinal &&
                    (descriptor == null || it.descriptor == descriptor)
            }
        return classes + ("SourceKt" to mutate(file, op, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun call(
        loaded: Map<String, ByteArray>,
        method: String,
        vararg args: Any,
    ) = loadAndInvoke(loaded, "SourceKt", method, *args)

    @ParameterizedTest
    @ValueSource(strings = ["local", "expr", "call"])
    fun `a computed boolean is replaced by the constant`(method: String) {
        // One input the method answers false to and one it answers true to.
        val (falsy, truthy) = if (method == "call") "x" to "" else -1 to 5

        assertEquals(false, call(classes, method, falsy), "$method original")
        assertEquals(true, call(mutant(TrueReturnsOperator, method), method, falsy), "$method true")
        assertEquals(false, call(mutant(FalseReturnsOperator, method), method, truthy), "$method false")
        assertEquals("ireturn -> true", sites(TrueReturnsOperator, method).single().description)
        assertEquals("ireturn -> false", sites(FalseReturnsOperator, method).single().description)
        assertEquals("", verify(mutant(TrueReturnsOperator, method).getValue("SourceKt")))
        assertEquals("", verify(mutant(FalseReturnsOperator, method).getValue("SourceKt")))
    }

    @Test
    fun `a constant return is a site only for the other constant`() {
        assertEquals(emptyList<Any>(), sites(TrueReturnsOperator, "yes"))
        assertEquals(emptyList<Any>(), sites(FalseReturnsOperator, "no"))
        assertEquals(true, call(classes, "yes"))
        assertEquals(false, call(mutant(FalseReturnsOperator, "yes"), "yes"))
        assertEquals(true, call(mutant(TrueReturnsOperator, "no"), "no"))
    }

    @Test
    fun `each return of a method is its own ordinal`() {
        assertEquals(listOf(0, 1), sites(TrueReturnsOperator, "twice").map { it.ordinal })
        // The first return is already ICONST_0, so only the second is a false-returns site.
        assertEquals(listOf(0), sites(FalseReturnsOperator, "twice").map { it.ordinal })
        assertEquals(listOf(false, false), listOf(-1, 1).map { call(classes, "twice", it) })
        assertEquals(
            listOf(true, false),
            listOf(-1, 1).map {
                call(mutant(TrueReturnsOperator, "twice", ordinal = 0), "twice", it)
            },
        )
        assertEquals(
            listOf(false, true),
            listOf(-1, 1).map {
                call(mutant(TrueReturnsOperator, "twice", ordinal = 1), "twice", it)
            },
        )
    }

    @Test
    fun `overloads are told apart by descriptor`() {
        val changed = mutant(FalseReturnsOperator, "over", "(I)Z")

        assertEquals(
            setOf("(I)Z", "(Ljava/lang/String;)Z"),
            sites(FalseReturnsOperator, "over").map { it.descriptor }.toSet(),
        )
        assertEquals(listOf(true, true), listOf("overInt", "overString").map { call(classes, it) })
        assertEquals(listOf(false, true), listOf("overInt", "overString").map { call(changed, it) })
    }

    @Test
    fun `long and double parameters do not disturb the stack`() {
        assertEquals(true, call(mutant(TrueReturnsOperator, "wide"), "wide", 1L, 5.0))
        assertEquals(false, call(mutant(FalseReturnsOperator, "wide"), "wide", 9L, 5.0))
        assertEquals("", verify(mutant(TrueReturnsOperator, "wide").getValue("SourceKt")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["boxed", "count", "unit", "suspending"])
    fun `non boolean primitive, boxed, void and suspend returns have no site`(method: String) {
        assertEquals(emptyList<Any>(), sites(TrueReturnsOperator, method) + sites(FalseReturnsOperator, method))
    }

    @Test
    fun `instance methods and property getters are sites`() {
        val flags = classes.getValue("Flags")
        val found = discover(flags, TrueReturnsOperator).map { it.methodName }.toSet()

        assertEquals(setOf("isOn", "getHasOn", "getOn"), found)
        discover(flags, FalseReturnsOperator).forEach {
            assertEquals("", verify(mutate(flags, FalseReturnsOperator, it, noLookup)))
        }
    }
}
