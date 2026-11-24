package com.github.rodrigotimoteo.mutkt.application.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class OutcomeTest {
    @Test
    fun `outcomes compare by value`() {
        assertEquals(Outcome.Killed("FooTest"), Outcome.Killed("FooTest"))
        assertNotEquals(Outcome.Killed("FooTest"), Outcome.Killed("BarTest"))
        assertEquals(Outcome.LoadError("bad"), Outcome.LoadError("bad"))
    }

    @Test
    fun `class file exposes its name and bytes`() {
        val file = ClassFile("a.Foo", byteArrayOf(1, 2))

        assertEquals("a.Foo", file.name)
        assertEquals(2, file.bytes.size)
    }
}
