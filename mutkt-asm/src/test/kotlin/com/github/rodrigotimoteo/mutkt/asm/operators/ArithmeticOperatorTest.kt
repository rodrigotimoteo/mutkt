package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val TYPES = listOf("Int", "Long", "Float", "Double")
private val OPS = mapOf("add" to "+", "sub" to "-", "mul" to "*", "div" to "/", "rem" to "%")

// One static function per operation and type, e.g. addLong(a: Long, b: Long) = a + b.
private val SOURCE =
    TYPES.joinToString("\n") { t ->
        OPS.entries.joinToString("\n") { (name, op) -> "fun ${name}$t(a: $t, b: $t) = a $op b" }
    } +
        """

        fun negInt(a: Int) = -a
        fun negDouble(a: Double) = -a
        fun bump(a: Int): Int {
            var x = a
            x++
            x += 2
            return x
        }
        fun concat(a: String, b: Any) = a + b + "!"
        fun template(a: String, n: Int) = "${'$'}a-${'$'}n"
        fun poly(a: Int, b: Int) = a * b + a - b
        fun mixed(a: Long, b: Int, c: Double) = (a + b) * c
        fun over(a: Int) = a + 1
        fun over(a: Long) = a + 1L
        class Calc { fun twice(a: Int) = a * 2 }
        """.trimIndent()

class ArithmeticOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        bytes: ByteArray = file,
    ) = discover(bytes, ArithmeticOperator).filter { it.methodName == method }

    private fun mutantClasses(
        method: String,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found = sites(method).single { it.ordinal == ordinal }
        return classes + ("SourceKt" to mutate(file, ArithmeticOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private val operands: Map<String, Pair<Any, Any>> =
        mapOf("Int" to (8 to 2), "Long" to (8L to 2L), "Float" to (8f to 2f), "Double" to (8.0 to 2.0))

    private fun invoke(
        all: Map<String, ByteArray>,
        method: String,
        type: String,
    ): Double {
        val (a, b) = operands.getValue(type)
        return (loadAndInvoke(all, "SourceKt", method, a, b) as Number).toDouble()
    }

    @ParameterizedTest
    @CsvSource(
        "add, sub, 10, 6",
        "sub, add, 6, 10",
        "mul, div, 16, 4",
        "div, mul, 4, 16",
        "rem, mul, 0, 16",
    )
    fun `each operation is swapped for every operand type`(
        op: String,
        to: String,
        before: Double,
        after: Double,
    ) {
        TYPES.forEach { type ->
            val method = op + type
            val site = sites(method).single()
            val mutant = mutantClasses(method)

            assertEquals(before, invoke(classes, method, type), "$method original")
            assertEquals(after, invoke(mutant, method, type), "$method mutant")
            assertEquals("${type[0].lowercaseChar()}$op -> ${type[0].lowercaseChar()}$to", site.description)
            assertEquals("", verify(mutant.getValue("SourceKt")), "$method failed verification")
        }
    }

    @Test
    fun `unary negation, increments and string building are not sites`() {
        listOf("negInt", "negDouble", "bump", "concat", "template").forEach {
            assertEquals(emptyList<Any>(), sites(it), "$it must have no site")
        }
    }

    @Test
    fun `ordinals follow instruction order within a method`() {
        val results = (0..2).map { loadAndInvoke(mutantClasses("poly", it), "SourceKt", "poly", 3, 2) }

        assertEquals(listOf("imul -> idiv", "iadd -> isub", "isub -> iadd"), sites("poly").map { it.description })
        assertEquals(7, loadAndInvoke(classes, "SourceKt", "poly", 3, 2))
        assertEquals(listOf(2, 1, 11), results)
    }

    @Test
    fun `long and double operands keep the stack balanced around two-slot values`() {
        assertEquals(listOf("ladd -> lsub", "dmul -> ddiv"), sites("mixed").map { it.description })
        assertEquals(64.0, loadAndInvoke(classes, "SourceKt", "mixed", 6L, 2, 8.0))
        listOf(32.0, 1.0).forEachIndexed { ordinal, expected ->
            val mutant = mutantClasses("mixed", ordinal)

            assertEquals(expected, loadAndInvoke(mutant, "SourceKt", "mixed", 6L, 2, 8.0))
            assertEquals("", verify(mutant.getValue("SourceKt")))
        }
    }

    @Test
    fun `overloads are separate sites told apart by descriptor`() {
        assertEquals(setOf("(I)I", "(J)J"), sites("over").map { it.descriptor }.toSet())
        assertEquals(listOf(0, 0), sites("over").map { it.ordinal })
    }

    @Test
    fun `instance methods of nested classes are found`() {
        val calc = classes.getValue("Calc")
        val found = sites("twice", calc).single()

        assertEquals("imul -> idiv", found.description)
        assertEquals("", verify(mutate(calc, ArithmeticOperator, found, noLookup)))
    }
}
