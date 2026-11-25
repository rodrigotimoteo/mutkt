package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.tree.LineNumberNode

class DataClassMembersTest {
    private val classes =
        compileKotlin(
            """
            data class Gen(val a: Int, val b: Long)
            data class Own(val a: Int) {
                override fun toString() = "Own " + (a + 1)
            }
            class Plain(val a: Int) {
                override fun equals(other: Any?) = other is Plain && other.a == a
                override fun hashCode() = a + 1
                override fun toString() = "Plain"
                operator fun component1() = a
                @JvmOverloads fun copy(a: Int = this.a) = Plain(a)
            }
            data object Single
            @JvmInline value class Meters(val v: Int)
            data class Tagged(val name: String, val size: Meters)
            """.trimIndent(),
        )

    private fun sitesIn(bytes: ByteArray) =
        Operators.all
            .flatMap { discover(bytes, it) }
            .map { it.methodName }
            .toSet()

    private fun sitesIn(className: String) = sitesIn(classes.getValue(className))

    @Test
    fun `generated data class members have no sites`() {
        assertEquals(setOf("getA", "getB"), sitesIn("Gen"))
    }

    @Test
    fun `a member the user overrides in a data class keeps its sites`() {
        assertEquals(setOf("getA", "toString"), sitesIn("Own"))
    }

    @Test
    fun `the same members declared in a regular class keep their sites`() {
        assertEquals(setOf("equals", "hashCode", "toString", "component1", "copy", "getA"), sitesIn("Plain"))
    }

    @Test
    fun `a JvmOverloads copy in a regular class keeps its sites`() {
        val copies =
            Operators.all
                .flatMap { discover(classes.getValue("Plain"), it) }
                .filter { it.methodName == "copy" }

        assertEquals(setOf("(I)LPlain;", "()LPlain;"), copies.map { it.descriptor }.toSet())
    }

    // Same lineless members as a data class, but without kotlin.Metadata the class is not Kotlin's.
    @Test
    fun `a class without kotlin Metadata keeps its sites`() {
        val node = parse(classes.getValue("Gen")).also { it.visibleAnnotations = null }

        assertEquals(true, sitesIn(node.toBytes()).containsAll(listOf("equals", "hashCode", "toString", "component1")))
    }

    @Test
    fun `generated data object members have no sites`() {
        assertEquals(emptySet<String>(), sitesIn("Single"))
    }

    // The wrappers delegate to the -impl statics, which hold the generated code and keep their sites.
    @Test
    fun `value class wrappers are skipped but their impl statics are not`() {
        val sites = sitesIn("Meters")

        assertEquals(emptySet<String>(), sites intersect setOf("toString", "hashCode", "equals"))
        assertEquals(true, sites.containsAll(listOf("toString-impl", "hashCode-impl", "equals-impl")))
    }

    // A value class in the signature mangles a name to "<name>-<hash>" (component2-..., copy-..., getSize-...).
    @Test
    fun `mangled data class members have no sites`() {
        assertEquals(setOf("getName", "getSize"), sitesIn("Tagged").map { it.substringBefore('-') }.toSet())
    }

    // The generated members are recognised by their missing line numbers, which every method lacks here.
    @Test
    fun `without debug info nothing is recognised`() {
        val node = parse(classes.getValue("Gen"))
        node.methods.forEach { m -> m.instructions.filterIsInstance<LineNumberNode>().forEach(m.instructions::remove) }

        assertEquals(true, sitesIn(node.toBytes()).containsAll(listOf("equals", "hashCode", "toString", "component1")))
    }
}
