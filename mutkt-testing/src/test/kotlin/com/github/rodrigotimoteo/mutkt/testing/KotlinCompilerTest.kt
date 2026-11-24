package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KotlinCompilerTest {
    @Test
    fun `compiles source to class bytes keyed by binary name`() {
        val classes = compileKotlin("package p\nclass A\nclass B")

        assertEquals(setOf("p.A", "p.B"), classes.keys)
        assertTrue(classes.values.all { it.take(4) == listOf(0xCA, 0xFE, 0xBA, 0xBE).map(Int::toByte) })
    }

    @Test
    fun `compiled top level function runs`() {
        val classes = compileKotlin("package p\nfun twice(x: Int) = x * 2")

        assertEquals(10, loadAndInvoke(classes, "p.SourceKt", "twice", 5))
    }

    @Test
    fun `invalid source fails fast`() {
        assertThrows<IllegalStateException> { compileKotlin("fun broken( =") }
    }
}
