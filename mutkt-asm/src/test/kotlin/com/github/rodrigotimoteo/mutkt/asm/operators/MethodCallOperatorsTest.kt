package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Found
import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    class Sink {
        var hits = 0
        fun bump() { hits++ }
        fun bumpWide(a: Long, b: Double, c: Int) { hits += a.toInt() + b.toInt() + c }
        fun total(a: Long, b: Double): Long = a + hits + b.toLong()
        fun flag(): Boolean = hits > 0
        fun ratio(): Double = hits * 1.5
        fun weight(): Float = hits * 2f
        fun name(): String = "n" + hits
        fun codes(): IntArray = intArrayOf(hits)
    }
    interface Namer { fun nameOf(i: Int): String }
    open class Base { open fun hi() {} }
    class Derived : Base() { override fun hi() { super.hi() } }

    class Priv {
        var total = 0L
        private fun addWide(a: Long, b: Int) { total += a + b }
        private fun twice(a: Long): Long = a * 2
        fun go(): Long { addWide(5L, 3); return twice(total) }
    }

    var pings = 0
    fun ping() { pings++ }
    fun addTo(box: IntArray, a: Long, b: Double) { box[0] += a.toInt() + b.toInt() }
    fun twice(a: Long): Long = a * 2
    fun withDefault(a: Int = 1): Int = a
    fun maybeUnit(a: Boolean): Unit? = if (a) Unit else null
    suspend fun sus(a: Int): String = a.toString()

    fun voidStatic(): Int { pings = 0; ping(); return pings }
    fun voidTryOnly(): Int { pings = 0; try { ping() } catch (e: Exception) { return -1 }; return pings }
    fun voidStaticWide(): Int { val box = IntArray(1); addTo(box, 10L, 2.5); return box[0] }
    fun voidInstance(): Int { val s = Sink(); s.bump(); return s.hits }
    fun voidInstanceWide(): Int { val s = Sink(); s.bumpWide(10L, 2.5, 3); return s.hits }
    fun twoVoid(): Int { val s = Sink(); s.bump(); s.bump(); return s.hits }
    fun superCall(): Unit = Derived().hi()
    fun intrinsics(s: String): Int = s.length

    fun privCalls(): Long = Priv().go()
    fun nvLong(): Long = Sink().total(5L, 1.5)
    fun nvDouble(): Double { val s = Sink(); s.hits = 2; return s.ratio() }
    fun nvFloat(): Float { val s = Sink(); s.hits = 2; return s.weight() }
    fun nvBoolean(): Int { val s = Sink(); s.hits = 2; return if (s.flag()) 1 else 0 }
    fun nvString(): String? = Sink().name()
    fun nvArray(): IntArray? = Sink().codes()
    fun nvStatic(): Long = twice(21L)
    fun nvInterface(): String? {
        val n = object : Namer { override fun nameOf(i: Int) = "n" + i }
        return n.nameOf(1)
    }
    fun nvDiscarded(): Int { Sink().total(1L, 2.0); return 3 }
    fun nvDefault(): Int = withDefault()
    fun nvUnit(): Int { maybeUnit(true); return 1 }
    suspend fun nvSuspend(): String = sus(1)
    """.trimIndent()

private const val BRIDGE = "withDefault\$default"

class MethodCallOperatorsTest {
    private val classes = compileKotlin(SOURCE)
    private val noLookup: Hierarchy = { null }

    private fun sites(
        op: Operator,
        method: String,
        className: String = "SourceKt",
    ) = discover(classes.getValue(className), op).filter { it.methodName == method }

    private fun mutant(
        op: Operator,
        method: String,
        call: String,
        className: String = "SourceKt",
    ): Map<String, ByteArray> {
        val found = sites(op, method, className).single { it.description.endsWith(".$call") }
        return classes + (className to mutate(classes.getValue(className), op, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun run(
        op: Operator,
        method: String,
        call: String,
    ): Pair<Any?, Any?> {
        val changed = mutant(op, method, call)
        assertEquals("", verify(changed.getValue("SourceKt")), "$method mutant")
        return loadAndInvoke(classes, "SourceKt", method) to loadAndInvoke(changed, "SourceKt", method)
    }

    @ParameterizedTest
    @CsvSource(
        "voidStatic,ping,1,0",
        "voidTryOnly,ping,1,0",
        "voidStaticWide,addTo,12,0",
        "voidInstance,bump,1,0",
        "voidInstanceWide,bumpWide,15,0",
    )
    fun `a void call is removed with its receiver and arguments`(
        method: String,
        call: String,
        before: Int,
        after: Int,
    ) {
        assertEquals(before to after, run(VoidMethodCallsOperator, method, call))
    }

    @Test
    fun `a private method call is a site and drops its receiver and wide arguments`() {
        val void = mutant(VoidMethodCallsOperator, "go", "addWide", "Priv")
        val nonVoid = mutant(NonVoidMethodCallsOperator, "go", "twice", "Priv")

        assertEquals(
            listOf("remove call Priv.addWide"),
            sites(VoidMethodCallsOperator, "go", "Priv").map { it.description },
        )
        assertEquals("", verify(void.getValue("Priv")))
        assertEquals("", verify(nonVoid.getValue("Priv")))
        assertEquals(16L, loadAndInvoke(classes, "SourceKt", "privCalls"))
        assertEquals(0L, loadAndInvoke(void, "SourceKt", "privCalls"))
        assertEquals(0L, loadAndInvoke(nonVoid, "SourceKt", "privCalls"))
    }

    @Test
    fun `each void call of a method is its own ordinal`() {
        assertEquals(listOf(0, 1), sites(VoidMethodCallsOperator, "twoVoid").map { it.ordinal })
        val first =
            mutate(
                classes.getValue("SourceKt"),
                VoidMethodCallsOperator,
                sites(VoidMethodCallsOperator, "twoVoid")[0],
                noLookup,
            )

        assertEquals(2, loadAndInvoke(classes, "SourceKt", "twoVoid"))
        assertEquals(1, loadAndInvoke(classes + ("SourceKt" to first), "SourceKt", "twoVoid"))
    }

    @ParameterizedTest
    @CsvSource(
        "nvLong,total,6,0",
        "nvDouble,ratio,3.0,0.0",
        "nvFloat,weight,4.0,0.0",
        "nvBoolean,flag,1,0",
        "nvStatic,twice,42,0",
        "nvDefault,withDefault\$default,1,0",
    )
    fun `a primitive-returning call is replaced by zero of its type`(
        method: String,
        call: String,
        before: String,
        after: String,
    ) {
        val (original, mutated) = run(NonVoidMethodCallsOperator, method, call)

        assertEquals(before, original.toString().removeSuffix("L"), method)
        assertEquals(after, mutated.toString(), method)
    }

    @ParameterizedTest
    @CsvSource("nvString,name", "nvInterface,nameOf")
    fun `an object-returning call is replaced by null`(
        method: String,
        call: String,
    ) {
        val (original, mutated) = run(NonVoidMethodCallsOperator, method, call)

        assertTrue(original != null && mutated == null, "$method: $original -> $mutated")
    }

    @Test
    fun `an array-returning call is replaced by null`() {
        assertEquals(null, run(NonVoidMethodCallsOperator, "nvArray", "codes").second)
    }

    @Test
    fun `a call whose result is discarded is still a site and stays valid`() {
        assertEquals(3 to 3, run(NonVoidMethodCallsOperator, "nvDiscarded", "total"))
    }

    @Test
    fun `void and non-void operators never share a call`() {
        val void = sites(VoidMethodCallsOperator, "voidInstance").map(Found::description)
        val nonVoid = sites(NonVoidMethodCallsOperator, "voidInstance").map(Found::description)

        assertEquals(listOf("remove call Sink.bump"), void)
        assertEquals(listOf("replace call with default: Sink.getHits"), nonVoid)
    }

    @Test
    fun `constructors and super calls are not sites`() {
        assertEquals(emptyList<Found>(), sites(VoidMethodCallsOperator, "hi", "Derived"))
        assertEquals(emptyList<Found>(), sites(NonVoidMethodCallsOperator, "hi", "Derived"))
        assertEquals(
            listOf("remove call Derived.hi"),
            sites(VoidMethodCallsOperator, "superCall").map { it.description },
        )
    }

    @Test
    fun `Intrinsics null checks are not sites`() {
        assertEquals(emptyList<Found>(), sites(VoidMethodCallsOperator, "intrinsics"))
        assertEquals(
            listOf(
                "replace call with default: String.length",
            ),
            sites(NonVoidMethodCallsOperator, "intrinsics").map {
                it.description
            },
        )
    }

    @Test
    fun `default argument bridges contain no sites but their callers do`() {
        assertEquals(emptyList<Found>(), sites(NonVoidMethodCallsOperator, BRIDGE))
        assertEquals(emptyList<Found>(), sites(VoidMethodCallsOperator, BRIDGE))
        assertEquals(1, sites(NonVoidMethodCallsOperator, "nvDefault").size)
    }

    @Test
    fun `kotlin Unit returns and suspend calls are not sites`() {
        assertEquals(emptyList<Found>(), sites(NonVoidMethodCallsOperator, "nvUnit"))
        assertEquals(emptyList<Found>(), sites(NonVoidMethodCallsOperator, "nvSuspend"))
        assertEquals(emptyList<Found>(), sites(VoidMethodCallsOperator, "nvSuspend"))
    }
}
