package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun retInt(a: Int) = a + 1
    fun retLong(a: Long) = a + 1L
    fun retFloat(a: Float) = a + 1f
    fun retDouble(a: Double) = a + 1.0
    fun retChar(a: Char): Char = a
    fun retShort(a: Short): Short = a
    fun retByte(a: Byte): Byte = a
    fun retBool(a: Int): Boolean = a > 0
    fun retString(a: Int): String = "x"
    fun retUnit(a: Int) { println(a) }
    fun zeroInt(): Int = 0
    fun zeroLong(): Long = 0L
    fun zeroFloat(): Float = 0f
    fun zeroDouble(): Double = 0.0
    fun branches(a: Int): Int {
        if (a > 0) return a
        if (a < -5) return 7
        return 0
    }
    fun over(a: Int) = a + 1
    fun over(a: Long) = a + 1L
    fun clamp(a: Int) = if (a > 0) a else 0
    class Outer { class Nested(private val v: Long) { fun get() = v } }
    """.trimIndent()

class ReturnValsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        bytes: ByteArray = file,
    ) = discover(bytes, ReturnValsOperator).filter { it.methodName == method }

    private fun mutantClasses(
        method: String,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found = sites(method).single { it.ordinal == ordinal }
        return classes + ("SourceKt" to mutate(file, ReturnValsOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    @ParameterizedTest
    @MethodSource("numericCases")
    fun `numeric returns become zero`(
        method: String,
        argument: Any,
        description: String,
    ) {
        val mutant = mutantClasses(method)

        assertEquals(6.0, (loadAndInvoke(classes, "SourceKt", method, argument) as Number).toDouble())
        assertEquals(0.0, (loadAndInvoke(mutant, "SourceKt", method, argument) as Number).toDouble())
        assertEquals("$description -> 0", sites(method).single().description)
        assertEquals("", verify(mutant.getValue("SourceKt")))
    }

    @Test
    fun `char, short and byte returns are zeroed`() {
        val cases = listOf("retChar" to 'a', "retShort" to 7.toShort(), "retByte" to 7.toByte())
        cases.forEach { (method, argument) ->
            val mutant = mutantClasses(method)
            val zero =
                (loadAndInvoke(mutant, "SourceKt", method, argument)).let {
                    if (it is Char) it.code else (it as Number).toInt()
                }

            assertEquals(0, zero, "$method mutant")
            assertEquals("ireturn -> 0", sites(method).single().description)
            assertEquals("", verify(mutant.getValue("SourceKt")))
        }
    }

    @Test
    fun `boolean, reference and unit returns are not sites`() {
        listOf("retBool", "retString", "retUnit").forEach {
            assertEquals(emptyList<Any>(), sites(it), "$it must have no site")
        }
    }

    @Test
    fun `a return right after a zero constant is not a site`() {
        listOf("zeroInt", "zeroLong", "zeroFloat", "zeroDouble").forEach {
            assertEquals(emptyList<Any>(), sites(it), "$it must have no site")
        }
    }

    @Test
    fun `only returns that are not already a zero constant count as sites in a method`() {
        val results = (0..1).map { loadAndInvoke(mutantClasses("branches", it), "SourceKt", "branches", 3) }

        assertEquals(2, sites("branches").size)
        assertEquals(listOf(0, 3), results)
        assertEquals(7, loadAndInvoke(mutantClasses("branches", 0), "SourceKt", "branches", -9))
        assertEquals(0, loadAndInvoke(mutantClasses("branches", 1), "SourceKt", "branches", -9))
    }

    @Test
    fun `overloads are separate sites told apart by descriptor`() {
        assertEquals(setOf("(I)I", "(J)J"), sites("over").map { it.descriptor }.toSet())
        assertEquals(listOf(0, 0), sites("over").map { it.ordinal })
    }

    @Test
    fun `a branch that can return a non-zero value stays a site`() {
        val sites = sites("clamp")
        val results = sites.indices.map { loadAndInvoke(mutantClasses("clamp", it), "SourceKt", "clamp", 5) }

        assertEquals(5, loadAndInvoke(classes, "SourceKt", "clamp", 5))
        assertEquals(true, sites.isNotEmpty() && 0 in results, "some site must zero the a path: $results")
    }

    @Test
    fun `instance methods of nested classes are found`() {
        val nested = classes.getValue("Outer\$Nested")
        val found = sites("get", nested).single()

        assertEquals("lreturn -> 0", found.description)
        assertEquals("", verify(mutate(nested, ReturnValsOperator, found, noLookup)))
    }

    companion object {
        @JvmStatic
        fun numericCases(): List<Arguments> =
            listOf(
                arguments("retInt", 5, "ireturn"),
                arguments("retLong", 5L, "lreturn"),
                arguments("retFloat", 5f, "freturn"),
                arguments("retDouble", 5.0, "dreturn"),
            )
    }
}
