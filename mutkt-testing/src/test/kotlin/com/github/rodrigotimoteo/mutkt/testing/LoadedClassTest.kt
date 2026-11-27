package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `calls and probe reads share one loader`() {
        val loaded = IsolatedClasses(classes)

        assertNull(loaded.probes("p.P", "hits"))
        loaded.invoke("p.SourceKt", "mark")

        assertArrayEquals(booleanArrayOf(true, false), loaded.probes("p.P", "hits"))
        assertNull(IsolatedClasses(classes).probes("p.P", "hits"))
        assertSame(loaded.type("p.P"), loaded.type("p.P"))
        assertNotSame(loaded.type("p.P"), IsolatedClasses(classes).type("p.P"))
    }

    @Test
    fun `a sandbox defines the classes again from their class file resources`() {
        val loaded = IsolatedClasses(classes)
        val sandbox = loaded.sandbox()

        sandbox.invoke("p.SourceKt", "mark")

        assertNotSame(loaded.type("p.P"), sandbox.type("p.P"))
        assertNull(loaded.probes("p.P", "hits"))
        assertArrayEquals(booleanArrayOf(true, false), sandbox.probes("p.P", "hits"))
        assertSame(String::class.java, sandbox.type("java.lang.String"))
    }

    @Test
    fun `probes from every loader of a class share one registry array`() {
        clearProbes()
        val first = probes("p.Q", 2)

        assertSame(first, probes("p.Q", 2))
        val registry = ClassLoader.getSystemClassLoader().loadClass(TEST_PROBE_REGISTRY)
        assertSame(first, registry.getMethod("probes", String::class.java, Integer.TYPE).invoke(null, "p.Q", 2))
        clearProbes()
        assertNotSame(first, probes("p.Q", 2))
    }

    private companion object {
        val classes =
            compileKotlin(
                """
                package p
                fun boom(): Int = error("boom")
                fun one() = 1
                object P { private var hits: BooleanArray? = null; fun mark() { hits = booleanArrayOf(true, false) } }
                fun mark() = P.mark()
                """.trimIndent(),
            )
    }
}
