package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private const val DATA_CLASSES =
    """
    data class Point(val x: Int, val y: Int) {
        fun shifted() = copy(x = x + 1).x
    }
    data class Wide(val a: Long, val b: Double, val c: String)
    @JvmInline value class Id(val v: Int)
    data class Tagged(val id: Id, val n: Int)
    """

private const val CALLERS =
    """
    class Mover { fun move(p: Point) = p.copy(x = 1).x }
    class Plain(val v: Int) { fun copy(v: Int): Plain = Plain(v) }
    class Other { fun copy(x: Int): Int = x }

    fun viaMover(a: Int) = Mover().move(Point(a, a))
    fun viaMember(a: Int) = Point(a, a).shifted()
    fun explicit(a: Int) = Point(a, a).copy(7, 8).x
    fun defaulted(a: Int) = Point(a, a).copy(y = 3).y
    fun chained(a: Int) = Point(a, a).copy(x = 1).copy(y = 2).y
    fun wide(a: Long) = Wide(a, 1.5, "s").copy(b = 2.5).a
    fun wideAll(a: Long) = Wide(a, 1.5, "s").copy(a + 1, 2.5, "t").a
    fun plain(a: Int) = Plain(a).copy(a + 1).v
    fun ordinary(a: Int) = Other().copy(a)
    fun mangled(a: Int) = Tagged(Id(a), a).copy(n = 2).n
    fun noCopy(a: Int) = Point(a, a).x
    """

private val AFTER = compileKotlin((CALLERS + DATA_CLASSES).trimIndent())
private val BEFORE = compileKotlin((DATA_CLASSES + CALLERS).trimIndent())

class DataClassCopyOperatorTest {
    private val classes = AFTER
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        className: String = "SourceKt",
        from: Map<String, ByteArray> = classes,
    ) = discover(from.getValue(className), DataClassCopyOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        ordinal: Int = 0,
        className: String = "SourceKt",
    ): Map<String, ByteArray> {
        val found = sites(method, className).single { it.ordinal == ordinal }
        return classes + (className to mutate(classes.getValue(className), DataClassCopyOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun run(
        method: String,
        vararg args: Any?,
        ordinal: Int = 0,
    ) = loadAndInvoke(mutant(method, ordinal), "SourceKt", method, *args)

    @Test
    fun `an explicit copy call becomes null, which fails when used`() {
        assertEquals("replace copy result with null: Point.copy", sites("explicit").single().description)
        assertEquals(7, loadAndInvoke(classes, "SourceKt", "explicit", 1))
        assertThrows(NullPointerException::class.java) { run("explicit", 1) }
        assertEquals("", verify(mutant("explicit").getValue("SourceKt")))
    }

    @Test
    fun `a copy with defaulted arguments goes through copy default`() {
        assertEquals("replace copy result with null: Point.copy\$default", sites("defaulted").single().description)
        assertEquals(3, loadAndInvoke(classes, "SourceKt", "defaulted", 1))
        assertThrows(NullPointerException::class.java) { run("defaulted", 1) }
        assertEquals("", verify(mutant("defaulted").getValue("SourceKt")))
    }

    @Test
    fun `chained copies are separate sites in order`() {
        assertEquals(listOf(0, 1), sites("chained").map { it.ordinal })
        assertEquals(2, loadAndInvoke(classes, "SourceKt", "chained", 1))
        // The inner copy becomes null, so the outer one is invoked on null.
        listOf(0, 1).forEach { ordinal ->
            assertThrows(NullPointerException::class.java) { run("chained", 1, ordinal = ordinal) }
            assertEquals("", verify(mutant("chained", ordinal).getValue("SourceKt")))
        }
    }

    @Test
    fun `long and double arguments are popped as two slots`() {
        assertEquals(1L, loadAndInvoke(classes, "SourceKt", "wide", 1L))
        assertEquals(2L, loadAndInvoke(classes, "SourceKt", "wideAll", 1L))
        listOf("wide", "wideAll").forEach { method ->
            assertThrows(NullPointerException::class.java) { run(method, 1L) }
            assertEquals("", verify(mutant(method).getValue("SourceKt")))
        }
    }

    @Test
    fun `a copy inside the data class itself is a site`() {
        assertEquals(1, sites("shifted", "Point").size)
        assertEquals(2, loadAndInvoke(classes, "SourceKt", "viaMember", 1))
        val bytes = mutant("shifted", className = "Point")
        assertEquals("", verify(bytes.getValue("Point")))
        assertThrows(NullPointerException::class.java) { loadAndInvoke(bytes, "SourceKt", "viaMember", 1) }
    }

    @Test
    fun `a copy called from another class is a site`() {
        assertEquals(1, sites("move", "Mover").size)
        val bytes = mutant("move", className = "Mover")
        assertEquals(1, loadAndInvoke(classes, "SourceKt", "viaMover", 4))
        // Loading links the class, so the JVM verifier has checked the mutant.
        assertThrows(NullPointerException::class.java) { loadAndInvoke(bytes, "SourceKt", "viaMover", 4) }
    }

    @Test
    fun `declaration order of the data class does not change the sites`() {
        fun summary(from: Map<String, ByteArray>) =
            from.keys.sorted().associateWith { name ->
                discover(from.getValue(name), DataClassCopyOperator).map { it.methodName + it.ordinal }
            }

        assertEquals(summary(AFTER), summary(BEFORE))
        assertEquals(1, summary(BEFORE).getValue("Mover").size)
    }

    @Test
    fun `ordinary methods named copy that do not return their owner are not sites`() {
        assertEquals(emptyList<Any>(), sites("ordinary") + sites("noCopy"))
    }

    // A value class parameter mangles the name to copy-<hash>, so the call is not recognised.
    @Test
    fun `a mangled copy of a value class property is not a site`() {
        assertEquals(emptyList<Any>(), sites("mangled"))
        val others =
            discover(classes.getValue("SourceKt"), NonVoidMethodCallsOperator).filter { it.methodName == "mangled" }
        assertEquals(1, others.count { it.description.contains("Tagged.copy-") })
    }

    // Known limit: without class context a same-shaped copy method of a non-data class is a site.
    @Test
    fun `a non-data class copy returning its own type has the same shape and is a site`() {
        assertEquals(1, sites("plain").size)
    }
}
