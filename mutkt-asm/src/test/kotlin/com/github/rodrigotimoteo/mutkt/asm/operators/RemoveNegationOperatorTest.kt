package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun negInt(a: Int) = -a
    fun negLong(a: Long) = -a
    fun negFloat(a: Float) = -a
    fun negDouble(a: Double) = -a
    fun negByte(b: Byte) = -b
    fun mixed(a: Int, b: Long) = -a + (-b).toInt()
    fun twice(a: Int) = -(-a)
    fun over(a: Int) = -a
    fun over(a: Double) = -a
    fun overInt() = over(3)
    fun overDouble() = over(3.0)
    fun lambda(xs: List<Int>) = xs.map { -it }.sum()
    fun lambdaOf(a: Int) = lambda(listOf(a, 1))
    fun none(a: Int, b: Int) = a - b + -5 + a.inv() + (0 - a)
    fun vec(v: Vec) = -v
    fun flip(x: Int) = Vec(x).flip()
    fun inner(a: Long) = Outer.Inner().neg(a)

    class Vec(val x: Int) {
        operator fun unaryMinus() = Vec(x + 1)
        fun flip() = -x
    }

    class Outer {
        class Inner {
            fun neg(a: Long) = -a
        }
    }
    """.trimIndent()

class RemoveNegationOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val noLookup: Hierarchy = { null }
    private val loader = IsolatedClasses(classes).type("SourceKt").classLoader

    private fun sites(
        method: String,
        cls: String = "SourceKt",
    ) = discover(classes.getValue(cls), RemoveNegationOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        cls: String = "SourceKt",
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found =
            sites(method, cls).single { it.ordinal == ordinal && (descriptor == null || it.descriptor == descriptor) }
        val bytes = mutate(classes.getValue(cls), RemoveNegationOperator, found, noLookup)
        assertEquals("", verify(bytes), "$found produced an invalid class")
        return classes + (cls to bytes)
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }.toString()

    private fun call(
        loaded: Map<String, ByteArray>,
        method: String,
        vararg args: Any,
    ) = loadAndInvoke(loaded, "SourceKt", method, *args)

    @Test
    fun `each primitive negation is removed`() {
        val cases = listOf("negInt" to 3, "negLong" to 3L, "negFloat" to 3f, "negDouble" to 3.0)

        cases.forEach { (method, arg) ->
            assertEquals(arg, call(mutant(method), method, arg), method)
        }
        assertEquals(listOf(-3, -3L, -3f, -3.0), cases.map { (method, arg) -> call(classes, method, arg) })
        assertEquals(
            listOf("ineg -> removed", "lneg -> removed", "fneg -> removed", "dneg -> removed"),
            cases.map { sites(it.first).single().description },
        )
    }

    @Test
    fun `a byte negation widened to int is a site`() {
        assertEquals(-3, call(classes, "negByte", 3.toByte()))
        assertEquals(3, call(mutant("negByte"), "negByte", 3.toByte()))
    }

    @Test
    fun `each negation of a method is its own ordinal`() {
        assertEquals(listOf("ineg -> removed", "lneg -> removed"), sites("mixed").map { it.description })
        assertEquals(-5, call(classes, "mixed", 2, 3L))
        assertEquals(-1, call(mutant("mixed", ordinal = 0), "mixed", 2, 3L))
        assertEquals(1, call(mutant("mixed", ordinal = 1), "mixed", 2, 3L))
    }

    @Test
    fun `a double negation is one site`() {
        assertEquals(1, sites("twice").size)
        assertEquals(-3, call(mutant("twice"), "twice", 3))
    }

    @Test
    fun `overloads are told apart by descriptor`() {
        assertEquals(listOf("(I)I", "(D)D"), sites("over").map { it.descriptor })
        val callers = listOf("overInt", "overDouble")
        val intChanged = mutant("over", descriptor = "(I)I")
        val doubleChanged = mutant("over", descriptor = "(D)D")

        assertEquals(listOf(-3, -3.0), callers.map { call(classes, it) })
        assertEquals(listOf(3, -3.0), callers.map { call(intChanged, it) })
        assertEquals(listOf(-3, 3.0), callers.map { call(doubleChanged, it) })
    }

    @Test
    fun `a negation in an inlined lambda body is a site`() {
        assertEquals(-3, call(classes, "lambdaOf", 2))
        assertEquals(3, call(mutant("lambda"), "lambdaOf", 2))
    }

    @Test
    fun `instance and nested class methods are sites`() {
        assertEquals(-4, call(classes, "flip", 4))
        assertEquals(4, call(mutant("flip", "Vec"), "flip", 4))
        assertEquals(-4L, call(classes, "inner", 4L))
        assertEquals(4L, call(mutant("neg", "Outer\$Inner"), "inner", 4L))
    }

    @Test
    fun `subtraction, negative constants, inv and an operator unaryMinus are not sites`() {
        assertEquals(emptyList<Any>(), sites("none") + sites("vec") + sites("unaryMinus", "Vec"))
    }
}
