package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun up(n: Int): Int {
        var i = n
        i++
        return i
    }
    fun down(n: Int): Int {
        var i = n
        i--
        return i
    }
    fun step(n: Int): Int {
        var i = n
        i += 5
        i -= 3
        return i
    }
    fun largeStep(n: Int): Int {
        var i = n
        i += 1000
        return i
    }
    fun over(n: Int): Int {
        var i = n
        i++
        return i
    }
    fun overInt(): Int = over(1)
    fun overLong(): Long = over(1L)
    fun over(n: Long): Long {
        var i = n
        i++
        return i
    }
    fun loop(n: Int): Int {
        var total = 0
        for (i in 0 until n) total += i
        return total
    }
    fun doubled(n: Double): Double {
        var d = n
        d++
        return d
    }
    fun plain(a: Int, b: Int) = a + b

    class Ticker {
        private var count = 0
        fun tick(n: Int): Int {
            var i = n
            i++
            count += 1
            return i
        }
    }
    """.trimIndent()

class IncrementsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(method: String) = discover(file, IncrementsOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found =
            sites(
                method,
            ).single { it.ordinal == ordinal && (descriptor == null || it.descriptor == descriptor) }
        return classes + ("SourceKt" to mutate(file, IncrementsOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun call(
        loaded: Map<String, ByteArray>,
        method: String,
        vararg args: Any,
    ) = loadAndInvoke(loaded, "SourceKt", method, *args)

    @Test
    fun `an increment becomes a decrement and the reverse`() {
        assertEquals(4, call(classes, "up", 3))
        assertEquals(2, call(classes, "down", 3))
        assertEquals(2, call(mutant("up"), "up", 3))
        assertEquals(4, call(mutant("down"), "down", 3))
        assertEquals("iinc +n -> -n", sites("up").single().description)
        assertEquals("iinc -n -> +n", sites("down").single().description)
        assertEquals("", verify(mutant("up").getValue("SourceKt")))
    }

    @Test
    fun `each increment of a method is its own ordinal`() {
        assertEquals(listOf(0, 1), sites("step").map { it.ordinal })
        assertEquals(2, call(classes, "step", 0))
        assertEquals(-8, call(mutant("step", ordinal = 0), "step", 0))
        assertEquals(8, call(mutant("step", ordinal = 1), "step", 0))
    }

    @Test
    fun `overloads are told apart by descriptor and long increments are not sites`() {
        val changed = mutant("over", "(I)I")

        assertEquals(listOf("(I)I"), sites("over").map { it.descriptor })
        assertEquals(listOf("2", "2"), listOf("overInt", "overLong").map { call(classes, it).toString() })
        assertEquals(listOf("0", "2"), listOf("overInt", "overLong").map { call(changed, it).toString() })
    }

    @Test
    fun `loop counters are sites`() {
        assertEquals(1, sites("loop").size)
        assertEquals("", verify(mutant("loop").getValue("SourceKt")))
    }

    @Test
    fun `double increments, plain addition and large constants have no site`() {
        assertEquals(emptyList<Any>(), sites("doubled") + sites("plain") + sites("largeStep"))
    }

    @Test
    fun `an instance method increment is a site`() {
        val ticker = classes.getValue("Ticker")
        val found = discover(ticker, IncrementsOperator).single { it.methodName == "tick" }

        assertEquals("", verify(mutate(ticker, IncrementsOperator, found, noLookup)))
    }

    @Test
    fun `a wide increment is flipped and the one that cannot be negated is not a site`() {
        // Kotlin only emits IINC for small constants, so the wide form needs hand-built bytecode.
        val bytes =
            buildClass("Wide", "m", "(I)I") {
                visitIincInsn(0, 1000)
                visitIincInsn(0, Short.MIN_VALUE.toInt())
                visitVarInsn(ILOAD, 0)
                visitInsn(IRETURN)
            }
        val found = discover(bytes, IncrementsOperator).single()
        val changed = mapOf("Wide" to mutate(bytes, IncrementsOperator, found, noLookup))

        assertEquals("iinc +n -> -n", found.description)
        assertEquals(-31768, loadAndInvoke(mapOf("Wide" to bytes), "Wide", "m", 0))
        assertEquals(-33768, loadAndInvoke(changed, "Wide", "m", 0))
        assertEquals("", verify(changed.getValue("Wide")))
    }
}
