package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.IRETURN

class LoadedClassTest {
    @Test
    fun `invokes in an isolated loader`() {
        assertEquals(1, loadAndInvoke(classes, "p.SourceKt", "one"))
        assertThrows<ClassNotFoundException> { Class.forName("p.SourceKt") }
    }

    @Test
    fun `given class shadows a classpath class of the same name`() {
        val shadow = LoadedClassTest::class.java.name
        val bytes =
            buildClass(shadow.replace('.', '/'), "answer") {
                visitIntInsn(BIPUSH, 42)
                visitInsn(IRETURN)
            }

        assertEquals(42, loadAndInvoke(mapOf(shadow to bytes), shadow, "answer"))
    }

    @Test
    fun `target exception is rethrown unwrapped`() {
        assertEquals(
            "boom",
            assertThrows<IllegalStateException> { loadAndInvoke(classes, "p.SourceKt", "boom") }.message,
        )
    }

    @Test
    fun `unknown method fails fast`() {
        assertThrows<NoSuchMethodException> { loadAndInvoke(classes, "p.SourceKt", "missing") }
    }

    private companion object {
        val classes = compileKotlin("package p\nfun boom(): Int = error(\"boom\")\nfun one() = 1")
    }
}
