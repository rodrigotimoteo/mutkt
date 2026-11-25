package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

class OperatorTest {
    private val classes =
        compileKotlin(
            """
            fun add(a: Int, b: Int) = a + b
            fun twice(a: Int, b: Int) = (a + b) + (a + b)
            fun subtract(a: Int, b: Int) = a - b
            """.trimIndent(),
        )
    private val original = classes.getValue("SourceKt")

    private fun method(
        node: ClassNode,
        name: String,
    ) = node.methods.single { it.name == name }

    @Test
    fun `sites lists matching instructions in instruction order`() {
        val node = parse(original)
        val method = method(node, "twice")

        val sites = ToyOperator.sites(method, node.info())

        val positions = sites.map { method.instructions.indexOf(it.instruction) }
        assertEquals(3, sites.size)
        assertEquals(positions.sorted(), positions)
        assertEquals("iadd -> isub", sites[0].description)
    }

    @Test
    fun `sites is empty when nothing matches and does not change the method`() {
        val node = parse(original)
        val subtract = method(node, "subtract")
        val before = subtract.instructions.map { it.opcode }

        assertTrue(ToyOperator.sites(subtract, node.info()).isEmpty())
        assertEquals(before, subtract.instructions.map { it.opcode })
    }

    @Test
    fun `sites is stateless across calls`() {
        val node = parse(original)
        val method = method(node, "add")

        assertEquals(ToyOperator.sites(method, node.info()), ToyOperator.sites(method, node.info()))
    }

    @Test
    fun `apply changes behavior of the loaded class and keeps it verifiable`() {
        val node = parse(original)
        val add = method(node, "add")
        ToyOperator.apply(add, ToyOperator.sites(add, node.info()).single())
        val mutated = node.toBytes()

        assertEquals(8, loadAndInvoke(classes, "SourceKt", "add", 5, 3))
        assertEquals(2, loadAndInvoke(classes + ("SourceKt" to mutated), "SourceKt", "add", 5, 3))
        assertFalse(original.contentEquals(mutated))
        val log = StringWriter()
        CheckClassAdapter.verify(ClassReader(mutated), false, PrintWriter(log))
        assertEquals("", log.toString())
    }
}
