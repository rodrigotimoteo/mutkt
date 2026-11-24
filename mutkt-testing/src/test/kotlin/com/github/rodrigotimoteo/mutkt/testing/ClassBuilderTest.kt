package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.ICONST_2
import org.objectweb.asm.Opcodes.ICONST_3
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IRETURN

class ClassBuilderTest {
    @Test
    fun `built method loads and returns the computed value`() {
        val bytes =
            buildClass("demo/Adder", "add") {
                visitInsn(ICONST_2)
                visitInsn(ICONST_3)
                visitInsn(IADD)
                visitInsn(IRETURN)
            }

        assertEquals(5, loadAndInvoke(mapOf("demo.Adder" to bytes), "demo.Adder", "add"))
    }

    @Test
    fun `descriptor selects arguments`() {
        val bytes =
            buildClass("demo/Id", "id", "(I)I") {
                visitVarInsn(ILOAD, 0)
                visitInsn(IRETURN)
            }

        assertEquals(7, loadAndInvoke(mapOf("demo.Id" to bytes), "demo.Id", "id", 7))
    }
}
