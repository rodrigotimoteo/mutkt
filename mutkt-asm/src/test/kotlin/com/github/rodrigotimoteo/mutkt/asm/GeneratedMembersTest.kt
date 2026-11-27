package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.tree.LineNumberNode

class GeneratedMembersTest {
    private val classes =
        compileKotlin(
            """
            enum class Color {
                RED, GREEN;
                fun next(): Color = if (this == RED) GREEN else RED
            }
            class Shelf { fun values(): Array<Shelf> = arrayOf(this) }
            fun interface Op { fun apply(x: Int): Int }
            fun wrapOp(f: (Int) -> Int): Op = Op(f)
            """.trimIndent(),
        )

    private fun sitesIn(bytes: ByteArray) =
        Operators.all
            .flatMap { discover(bytes, it) }
            .map { it.methodName }
            .toSet()

    private val adapter = classes.getValue("SourceKt\$sam\$Op\$0")

    @Test
    fun `generated enum members have no sites`() {
        assertEquals(setOf("next"), sitesIn(classes.getValue("Color")))
    }

    @Test
    fun `a values method of a regular class keeps its sites`() {
        assertEquals(setOf("values"), sitesIn(classes.getValue("Shelf")))
    }

    // Some kotlinc versions give these members a line number and some do not, so lines do not decide.
    @Test
    fun `enum members are recognised without debug info`() {
        val node = parse(classes.getValue("Color"))
        node.methods.forEach { m -> m.instructions.filterIsInstance<LineNumberNode>().forEach(m.instructions::remove) }

        assertEquals(setOf("next"), sitesIn(node.toBytes()))
    }

    @Test
    fun `an enum without kotlin Metadata keeps its members' sites`() {
        val node = parse(classes.getValue("Color")).also { it.visibleAnnotations = null }

        assertEquals(setOf("next", "values", "valueOf", "getEntries"), sitesIn(node.toBytes()))
    }

    // A function value converted to a fun interface; a lambda literal would use invokedynamic instead.
    @Test
    fun `a SAM adapter has no sites`() {
        assertEquals(emptySet<String>(), sitesIn(adapter))
    }

    @Test
    fun `the same class without the synthetic flag keeps its sites`() {
        val node = parse(adapter).also { it.access = it.access and ACC_SYNTHETIC.inv() }

        assertEquals(true, sitesIn(node.toBytes()).containsAll(listOf("equals", "hashCode")))
    }
}
