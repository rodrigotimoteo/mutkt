package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.info
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    class Box(val v: Int)
    class Wide(val a: Long, val b: Double, val c: Int)
    class Wrap(val inner: Box, val tag: String = "t")
    data class Point(val x: Int, val y: Int)
    open class Base(val n: Int)
    class Child(n: Int) : Base(n) { constructor() : this(1) }
    object Reg { fun make(a: Int) = Box(a).v }
    class Owner { companion object { fun create(a: Int) = Box(a).v } }

    fun make(a: Int) = Box(a).v
    fun wide(a: Long, d: Double) = Wide(a, d, 1).c
    fun nested(a: Int) = Wrap(Box(a)).inner.v
    fun defaulted(a: Int) = Wrap(Box(a)).tag.length
    fun explicitArg(a: Int) = Wrap(Box(a), "u").tag.length
    fun assigned(a: Int): Box {
        val b = Box(a)
        return b
    }
    fun branching(f: Boolean, a: Int) = Box(if (f) a else -a).v
    fun point(a: Int) = Point(a, a).x
    fun javaList(a: Int) = ArrayList<Int>(a).size
    fun viaObject(a: Int) = Reg.make(a)
    fun viaCompanion(a: Int) = Owner.create(a)
    fun direct(a: Int) = Box(a)
    fun directNested(a: Int) = Wrap(Box(a))
    fun fail(): Nothing = throw IllegalStateException("x")
    fun builder(a: String) = StringBuilder(a).length
    fun template(a: Int) = "v${'$'}a"
    fun noConstruction(a: Int) = a + 1
    """.trimIndent()

class ConstructorCallsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        bytes: ByteArray = file,
    ) = discover(bytes, ConstructorCallsOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        ordinal: Int = 0,
        className: String = "SourceKt",
    ): Map<String, ByteArray> {
        val bytes = classes.getValue(className)
        val found = sites(method, bytes).single { it.ordinal == ordinal }
        return classes + (className to mutate(bytes, ConstructorCallsOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun run(
        method: String,
        vararg args: Any?,
        ordinal: Int = 0,
    ) = loadAndInvoke(mutant(method, ordinal), "SourceKt", method, *args)

    @Test
    fun `a plain construction becomes null, which fails when used`() {
        assertEquals("new Box -> null", sites("make").single().description)
        assertEquals(3, loadAndInvoke(classes, "SourceKt", "make", 3))
        assertThrows(NullPointerException::class.java) { run("make", 3) }
        assertEquals("", verify(mutant("make").getValue("SourceKt")))
    }

    @Test
    fun `long and double args are popped as two slots`() {
        assertEquals(1, loadAndInvoke(classes, "SourceKt", "wide", 1L, 2.0))
        assertThrows(NullPointerException::class.java) { run("wide", 1L, 2.0) }
        assertEquals("", verify(mutant("wide").getValue("SourceKt")))
    }

    @Test
    fun `nested constructions are two sites, inner first`() {
        assertEquals(listOf("new Box -> null", "new Wrap -> null"), sites("nested").map { it.description })
        assertEquals(listOf(0, 1), sites("nested").map { it.ordinal })
        assertThrows(NullPointerException::class.java) { run("nested", 1, ordinal = 1) }
        // Kotlin null-checks constructor parameters, so a null inner Box is rejected by Wrap.
        assertThrows(NullPointerException::class.java) { run("nested", 1, ordinal = 0) }
        assertEquals("", verify(mutant("nested", 0).getValue("SourceKt")))
        assertEquals("", verify(mutant("nested", 1).getValue("SourceKt")))
    }

    @Test
    fun `a constructor call using its default argument is a site`() {
        assertEquals(2, sites("defaulted").size)
        assertEquals(1, loadAndInvoke(classes, "SourceKt", "defaulted", 1))
        assertThrows(NullPointerException::class.java) { run("defaulted", 1, ordinal = 1) }
        assertEquals("", verify(mutant("defaulted", 1).getValue("SourceKt")))
        assertEquals(2, sites("explicitArg").size)
    }

    @Test
    fun `an assigned construction becomes a null local that is returned`() {
        assertNotNull(loadAndInvoke(classes, "SourceKt", "assigned", 5))
        assertNull(run("assigned", 5))
    }

    @Test
    fun `a construction whose args contain a branch verifies`() {
        assertEquals("", verify(mutant("branching").getValue("SourceKt")))
        assertThrows(NullPointerException::class.java) { run("branching", true, 4) }
    }

    @Test
    fun `data class and java class constructions are sites`() {
        assertEquals("new Point -> null", sites("point").single().description)
        assertThrows(NullPointerException::class.java) { run("point", 1) }
        assertEquals("new java.util.ArrayList -> null", sites("javaList").single().description)
        assertThrows(NullPointerException::class.java) { run("javaList", 1) }
    }

    @Test
    fun `object and companion members are sites seen through their callers`() {
        assertEquals(1, loadAndInvoke(classes, "SourceKt", "viaObject", 1))
        val inObject = mutant("make", className = "Reg")
        val inCompanion = mutant("create", className = "Owner\$Companion")

        assertThrows(NullPointerException::class.java) { loadAndInvoke(inObject, "SourceKt", "viaObject", 1) }
        assertThrows(NullPointerException::class.java) { loadAndInvoke(inCompanion, "SourceKt", "viaCompanion", 1) }
        assertEquals("", verify(inObject.getValue("Reg")))
    }

    @Test
    fun `super and this calls in constructors are not sites`() {
        listOf("Child", "Base", "Box").forEach { name ->
            val node = parse(classes.getValue(name))
            val init = node.methods.filter { it.name == "<init>" }
            assertEquals(emptyList<Any>(), init.flatMap { ConstructorCallsOperator.sites(it, node.info()) }, name)
        }
    }

    @Test
    fun `string builders and thrown exceptions are noise, not sites`() {
        assertEquals(emptyList<Any>(), sites("builder") + sites("fail") + sites("template"))
    }

    @Test
    fun `a directly returned construction is left to null-returns`() {
        assertEquals(emptyList<Any>(), sites("direct"))
        // Only the inner Box is a site; the returned Wrap is not.
        assertEquals(listOf("new Box -> null"), sites("directNested").map { it.description })
    }

    @Test
    fun `a method without construction has no site`() {
        assertEquals(emptyList<Any>(), sites("noConstruction"))
    }

    @Test
    fun `a NEW without the DUP is not a site`() {
        val bytes =
            buildClass("Odd", "m") {
                visitTypeInsn(NEW, "java/lang/Object")
                visitVarInsn(ASTORE, 0)
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(ICONST_0)
                visitInsn(IRETURN)
            }

        assertEquals(emptyList<Any>(), sites("m", bytes))
    }
}
