package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.IRETURN

class ClassInfoTest {
    private val kotlin = compileKotlin("open class Base\nclass Derived : Base()")

    @Test
    fun `a Kotlin class carries its name, metadata flag and superclass`() {
        assertEquals(
            ClassInfo("Derived", isKotlin = true, superName = "Base"),
            parse(kotlin.getValue("Derived")).info(),
        )
    }

    @Test
    fun `a class without the kotlin Metadata annotation is not Kotlin`() {
        val bytes =
            buildClass("Plain", "m") {
                visitInsn(ICONST_0)
                visitInsn(IRETURN)
            }

        assertEquals(ClassInfo("Plain", isKotlin = false, superName = "java/lang/Object"), parse(bytes).info())
    }
}
