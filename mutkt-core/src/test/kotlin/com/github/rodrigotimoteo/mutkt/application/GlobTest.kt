package com.github.rodrigotimoteo.mutkt.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GlobTest {
    // pattern, name, expected
    private val cases =
        listOf(
            Triple("com.acme.Foo", "com.acme.Foo", true),
            Triple("com.acme.Foo", "com.acme.Foo2", false),
            Triple("com.acme.Foo", "xcom.acme.Foo", false),
            Triple("com.acme.Foo", "com.acmeXFoo", false),
            Triple("com.acme.*", "com.acme.Foo", true),
            Triple("com.acme.*", "com.acme.sub.Foo", false),
            Triple("com.acme.**", "com.acme.sub.Foo", true),
            Triple("com.acme.**", "com.acme.Foo", true),
            Triple("com.acme.**", "com.other.Foo", false),
            Triple("**", "a.b.C", true),
            Triple("*", "a.b.C", false),
            Triple("*", "C", true),
            Triple("**Test", "com.acme.FooTest", true),
            Triple("**Test", "com.acme.FooTests", false),
            Triple("com.acme.Foo?", "com.acme.Foo1", true),
            Triple("com.acme.Foo?", "com.acme.Foo", false),
            Triple("com.acme.Foo?", "com.acme.Foo12", false),
            Triple("com?acme.Foo", "com.acme.Foo", false),
            Triple("com.acme.Foo\$Inner", "com.acme.Foo\$Inner", true),
            Triple("com.acme.Foo\$*", "com.acme.Foo\$Inner", true),
            Triple("a+b(c)[d]", "a+b(c)[d]", true),
            Triple("a.b", "axb", false),
            Triple("com.acme.*", "com.acme.Foo\$Inner", true),
            Triple("a\\b", "a\\b", true),
            Triple("a\\Eb", "a\\Eb", true),
            Triple("a\\Eb", "aEb", false),
        )

    @Test
    fun `patterns match whole names only`() {
        val failures =
            cases.filter { (pattern, name, expected) -> Glob(pattern).matches(name) != expected }
        assertEquals(emptyList<Triple<String, String, Boolean>>(), failures)
    }

    @Test
    fun `double star between segments needs a segment so com dot star star dot Foo does not match com dot Foo`() {
        assertEquals(false, Glob("com.**.Foo").matches("com.Foo"))
        assertEquals(true, Glob("com.**.Foo").matches("com.a.Foo"))
    }

    @Test
    fun `empty and blank patterns are rejected`() {
        listOf("", " ", "\t").forEach { pattern ->
            val error = assertThrows(IllegalArgumentException::class.java) { Glob(pattern) }
            assertEquals("Glob pattern must not be blank", error.message)
        }
    }

    @Test
    fun `globs with the same pattern are equal`() {
        assertEquals(Glob("a.*"), Glob("a.*"))
    }
}
