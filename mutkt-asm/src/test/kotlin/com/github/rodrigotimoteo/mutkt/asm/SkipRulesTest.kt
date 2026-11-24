package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.Opcodes.ACC_BRIDGE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.tree.MethodNode

class SkipRulesTest {
    // Generated and user shapes in one compilation; keys are "Class.name(desc)".
    private val mutable: Map<String, Boolean> =
        compileKotlin(
            """
            abstract class Shape {
                abstract fun area(): Int
                fun twice(): Int = area() * 2
                fun withDefault(a: Int = 1, b: Int = 2) = a + b
                fun lambda(): () -> Int = { 3 + 4 }
                external fun native(): Int
            }
            class Box : Comparable<Box> {
                override fun compareTo(other: Box) = 0
                companion object { val shared = 1 }
            }
            data class Point(val x: Int, val y: Int)
            enum class Color { RED }
            """.trimIndent(),
        ).flatMap { (name, bytes) -> keyed(name, bytes) }.toMap()

    private fun keyed(
        className: String,
        bytes: ByteArray,
    ) = parse(bytes).methods.map { "$className.${it.name}${it.desc}" to isMutable(it) }

    private fun isMutable(key: String) = mutable.getValue(key)

    @Test
    fun `regular methods are mutable`() {
        assertEquals(true, isMutable("Shape.twice()I"))
        assertEquals(true, isMutable("Box.compareTo(LBox;)I"))
        assertEquals(true, isMutable("Point.component1()I"))
    }

    @Test
    fun `default-argument and bridge synthetics are skipped`() {
        assertEquals(false, isMutable("Shape.withDefault\$default(LShape;IIILjava/lang/Object;)I"))
        assertEquals(false, isMutable("Box.compareTo(Ljava/lang/Object;)I"))
        assertEquals(false, isMutable("Color.\$values()[LColor;"))
    }

    @Test
    fun `abstract and native methods are skipped`() {
        assertEquals(false, isMutable("Shape.area()I"))
        assertEquals(false, isMutable("Shape.native()I"))
    }

    @Test
    fun `constructors and static initializers are skipped`() {
        assertEquals(false, isMutable("Shape.<init>()V"))
        assertEquals(false, isMutable("Box.<clinit>()V"))
        assertEquals(false, isMutable("Color.<clinit>()V"))
    }

    // Kotlin lambda bodies are private static but not synthetic, and they hold user code.
    @Test
    fun `lambda bodies are mutable`() {
        assertEquals(true, mutable.entries.single { "lambda\$" in it.key }.value)
    }

    @ParameterizedTest
    @ValueSource(ints = [ACC_BRIDGE, ACC_SYNTHETIC])
    fun `each generated flag alone skips the method`(flag: Int) {
        assertEquals(false, isMutable(MethodNode(ACC_PUBLIC or flag, "m", "()V", null, null)))
    }
}
