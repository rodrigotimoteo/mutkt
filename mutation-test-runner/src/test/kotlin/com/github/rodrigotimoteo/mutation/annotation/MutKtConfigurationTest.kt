package com.github.rodrigotimoteo.mutation.annotation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MutKtConfigurationTest {
    @Test
    fun `default mode is STRICT`() {
        val config = MutKtConfiguration()
        assertEquals(VerificationMode.STRICT, config.mode)
    }

    @Test
    fun `default timeoutMs is 30_000`() {
        val config = MutKtConfiguration()
        assertEquals(30_000L, config.timeoutMs)
    }

    @Test
    fun `default skipInIDE is true`() {
        val config = MutKtConfiguration()
        assertTrue(config.skipInIDE)
    }

    @Test
    fun `default includePatterns is empty`() {
        val config = MutKtConfiguration()
        assertTrue(config.includePatterns.isEmpty())
    }

    @Test
    fun `default excludePatterns has 3 entries`() {
        val config = MutKtConfiguration()
        assertEquals(3, config.excludePatterns.size)
        assertTrue(config.excludePatterns.contains("**/*Test*"))
        assertTrue(config.excludePatterns.contains("**/*Generated*"))
        assertTrue(config.excludePatterns.contains("**/*Builder*"))
    }

    @Test
    fun `copy with different mode`() {
        val config = MutKtConfiguration()
        val modified = config.copy(mode = VerificationMode.LENIENT)
        assertEquals(VerificationMode.LENIENT, modified.mode)
        assertEquals(config.timeoutMs, modified.timeoutMs)
    }

    @Test
    fun `equality based on all fields`() {
        val a = MutKtConfiguration()
        val b = MutKtConfiguration()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when any field differs`() {
        val a = MutKtConfiguration()
        val b = a.copy(timeoutMs = 60_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `toString includes all fields`() {
        val config = MutKtConfiguration()
        val str = config.toString()
        assertTrue(str.contains("STRICT"))
        assertTrue(str.contains("30000"))
        assertTrue(str.contains("skipInIDE"))
    }

    @Test
    fun `VerificationMode has 3 values`() {
        assertEquals(3, VerificationMode.values().size)
    }

    @Test
    fun `VerificationMode valueOf works`() {
        assertEquals(VerificationMode.STRICT, VerificationMode.valueOf("STRICT"))
        assertEquals(VerificationMode.LENIENT, VerificationMode.valueOf("LENIENT"))
        assertEquals(VerificationMode.DISABLED, VerificationMode.valueOf("DISABLED"))
    }

    @Test
    fun `VerificationMode name returns expected string`() {
        assertEquals("STRICT", VerificationMode.STRICT.name)
        assertEquals("LENIENT", VerificationMode.LENIENT.name)
        assertEquals("DISABLED", VerificationMode.DISABLED.name)
    }

    @Test
    fun `custom excludePatterns replaces defaults`() {
        val config =
            MutKtConfiguration(
                excludePatterns = listOf("**/*Foo*", "**/*Bar*"),
            )
        assertEquals(2, config.excludePatterns.size)
        assertFalse(config.excludePatterns.contains("**/*Test*"))
    }
}
