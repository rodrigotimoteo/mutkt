package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoverTest {
    private val classes =
        compileKotlin(
            """
            fun add(a: Int, b: Int) = a + b
            fun add(a: Int, b: Int, c: Int) = a + b + c
            fun spread(a: Int, b: Int): Int {
                val x = a + b
                return x + 1
            }
            fun subtract(a: Int, b: Int) = a - b

            class Box(val a: Int, val b: Int) {
                val sum = a + b
                fun plain() = a + b
                fun withDefault(x: Int = 1, y: Int = 2) = x + y
            }
            """.trimIndent(),
        )
    private val file = classes.getValue("SourceKt")

    @Test
    fun `overloads are told apart by descriptor and ordinals count per method`() {
        val found = discover(file, ToyOperator).filter { it.methodName == "add" }

        assertEquals(
            listOf(
                Triple("(II)I", 0, "iadd -> isub"),
                Triple("(III)I", 0, "iadd -> isub"),
                Triple("(III)I", 1, "iadd -> isub"),
            ),
            found.map { Triple(it.descriptor, it.ordinal, it.description) },
        )
        assertEquals(setOf("SourceKt"), found.map { it.className }.toSet())
    }

    @Test
    fun `line is the nearest preceding line number`() {
        val found = discover(file, ToyOperator).filter { it.methodName == "spread" }

        assertEquals(listOf(4, 5), found.map { it.line })
        assertEquals(listOf(0, 1), found.map { it.ordinal })
    }

    @Test
    fun `line is null when the class has no debug info`() {
        val node = parse(file)
        node.methods.forEach { m ->
            m.instructions.filterIsInstance<org.objectweb.asm.tree.LineNumberNode>().forEach(m.instructions::remove)
        }

        assertEquals(
            listOf<Int?>(null, null),
            discover(node.toBytes(), ToyOperator)
                .filter {
                    it.methodName == "spread"
                }.map { it.line },
        )
    }

    @Test
    fun `skip rules keep initializers and generated methods out`() {
        val found = discover(classes.getValue("Box"), ToyOperator)

        assertEquals(setOf("plain", "withDefault"), found.map { it.methodName }.toSet())
    }

    @Test
    fun `nothing is found when no site matches`() {
        assertEquals(emptyList<Found>(), discover(file, ToyOperator).filter { it.methodName == "subtract" })
    }

    @Test
    fun `two calls on the same bytes agree`() {
        assertEquals(discover(file, ToyOperator), discover(file, ToyOperator))
    }
}
