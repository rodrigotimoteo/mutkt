package com.example.multishared

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedLogicTest {

    private lateinit var logic: SharedLogic

    @Before
    fun setUp() {
        // Touch the ApplicationProvider so the Robolectric
        // Android-runtime is actually initialised. SharedLogic
        // itself is pure-Kotlin, but the Robolectric runner
        // sets up the Android classpath the mutation task
        // depends on at execution time.
        ApplicationProvider.getApplicationContext<android.content.Context>()
        logic = SharedLogic()
    }

    @Test
    fun `describe returns negative for negative values`() {
        assertEquals("negative", logic.describe(-1))
        assertEquals("negative", logic.describe(-100))
    }

    @Test
    fun `describe returns zero for zero`() {
        assertEquals("zero", logic.describe(0))
    }

    @Test
    fun `describe returns one for one`() {
        assertEquals("one", logic.describe(1))
    }

    @Test
    fun `describe returns small for values between two and nine`() {
        assertEquals("small", logic.describe(2))
        assertEquals("small", logic.describe(9))
    }

    @Test
    fun `describe returns large for values ten and above`() {
        assertEquals("large", logic.describe(10))
        assertEquals("large", logic.describe(1000))
    }

    @Test
    fun `sumPositive ignores non-positive values`() {
        assertEquals(0, logic.sumPositive(emptyList()))
        assertEquals(0, logic.sumPositive(listOf(0, -1, -2)))
        assertEquals(6, logic.sumPositive(listOf(1, 2, 3)))
        assertEquals(3, logic.sumPositive(listOf(-1, 0, 1, 2)))
    }

    @Test
    fun `isPalindrome returns true for empty and single-character strings`() {
        assertTrue(logic.isPalindrome(""))
        assertTrue(logic.isPalindrome("a"))
    }

    @Test
    fun `isPalindrome detects simple palindromes`() {
        assertTrue(logic.isPalindrome("aba"))
        assertTrue(logic.isPalindrome("racecar"))
    }

    @Test
    fun `isPalindrome is case-insensitive`() {
        assertTrue(logic.isPalindrome("AbBa"))
        assertTrue(logic.isPalindrome("RaceCar"))
    }

    @Test
    fun `isPalindrome returns false for non-palindromes`() {
        assertFalse(logic.isPalindrome("abc"))
        assertFalse(logic.isPalindrome("hello"))
    }

    @Test
    fun `normalize handles null and blank inputs`() {
        assertEquals("<empty>", logic.normalize(null))
        assertEquals("<empty>", logic.normalize(""))
        assertEquals("<empty>", logic.normalize("   "))
    }

    @Test
    fun `normalize trims and uppercases regular strings`() {
        assertEquals("HELLO", logic.normalize("hello"))
        assertEquals("HELLO", logic.normalize("  hello  "))
        assertEquals("HELLO WORLD", logic.normalize("  hello world  "))
    }
}
