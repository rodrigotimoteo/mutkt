package com.github.rodrigotimoteo.mutation.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerificationModeTest {
    @Test
    fun `enum has exactly three values`() {
        val values = VerificationMode.values()
        assertEquals(3, values.size)
    }

    @Test
    fun `enum contains STRICT LENIENT DISABLED`() {
        val values = VerificationMode.values().map { it.name }.toSet()
        assertTrue(values.contains("STRICT"))
        assertTrue(values.contains("LENIENT"))
        assertTrue(values.contains("DISABLED"))
    }

    @Test
    fun `name returns expected string for STRICT`() {
        assertEquals("STRICT", VerificationMode.STRICT.name)
    }

    @Test
    fun `name returns expected string for LENIENT`() {
        assertEquals("LENIENT", VerificationMode.LENIENT.name)
    }

    @Test
    fun `name returns expected string for DISABLED`() {
        assertEquals("DISABLED", VerificationMode.DISABLED.name)
    }

    @Test
    fun `valueOf works for valid names`() {
        assertEquals(VerificationMode.STRICT, VerificationMode.valueOf("STRICT"))
        assertEquals(VerificationMode.LENIENT, VerificationMode.valueOf("LENIENT"))
        assertEquals(VerificationMode.DISABLED, VerificationMode.valueOf("DISABLED"))
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows<IllegalArgumentException> {
            VerificationMode.valueOf("INVALID")
        }
    }
}
