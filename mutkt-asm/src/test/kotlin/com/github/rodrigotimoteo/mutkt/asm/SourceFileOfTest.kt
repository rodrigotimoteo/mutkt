package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.IRETURN

class SourceFileOfTest {
    @Test
    fun `a compiled class and its nested classes name the file they were compiled from`() {
        val classes = compileKotlin("class Repo_Impl {\n    class Nested\n}", fileName = "Repo_Impl.kt")

        assertEquals(setOf("Repo_Impl.kt"), classes.values.map(::sourceFileOf).toSet())
    }

    @Test
    fun `a class without the attribute has no source file`() {
        val bytes =
            buildClass("Plain", "m") {
                visitInsn(ICONST_0)
                visitInsn(IRETURN)
            }

        assertNull(sourceFileOf(bytes))
    }
}
