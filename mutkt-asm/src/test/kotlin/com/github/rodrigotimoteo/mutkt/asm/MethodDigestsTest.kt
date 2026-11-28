package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MethodDigestsTest {
    private fun digests(source: String) = methodDigests(compileKotlin(source).getValue("a.Gate"))

    private val original =
        """
        package a

        class Gate {
            fun check(age: Int): Boolean {
                val limit = 18
                return age >= limit
            }

            fun other(age: Int) = age > 1
        }
        """.trimIndent()

    @Test
    fun `keys each method by name and descriptor`() =
        assertEquals(setOf("<init>()V", "check(I)Z", "other(I)Z"), digests(original).keys)

    @Test
    fun `unchanged code compiled again keeps its digests`() = assertEquals(digests(original), digests(original))

    @Test
    fun `an edit that only moves lines keeps the digests`() {
        val moved =
            original
                .replace(
                    "class Gate {",
                    "// a comment\n\nclass Gate {\n",
                ).replace("val limit", "\n\nval limit")

        assertEquals(digests(original), digests(moved))
    }

    @Test
    fun `a code change in a method changes only that method's digest`() {
        val changed = digests(original.replace("val limit = 18", "if (age > 120) return false\n        val limit = 18"))

        assertNotEquals(digests(original).getValue("check(I)Z"), changed.getValue("check(I)Z"))
        assertEquals(digests(original).getValue("other(I)Z"), changed.getValue("other(I)Z"))
    }
}
