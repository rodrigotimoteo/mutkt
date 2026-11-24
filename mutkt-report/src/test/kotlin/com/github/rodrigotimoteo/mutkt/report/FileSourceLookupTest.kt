package com.github.rodrigotimoteo.mutkt.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileSourceLookupTest {
    @TempDir
    lateinit var dir: Path

    private fun source(
        root: Path,
        relative: String,
        text: String,
    ) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    @Test
    fun `finds a line in a kotlin file`() {
        source(dir, "com/acme/Foo.kt", "one\ntwo\nthree\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertEquals("one", lookup.lineText("com.acme.Foo", 1))
        assertEquals("three", lookup.lineText("com.acme.Foo", 3))
    }

    @Test
    fun `finds a line in a java file`() {
        source(dir, "com/acme/Foo.java", "class Foo {}\n")
        assertEquals("class Foo {}", FileSourceLookup(listOf(dir)).lineText("com.acme.Foo", 1))
    }

    @Test
    fun `inner class resolves to the outer file`() {
        source(dir, "com/acme/Foo.kt", "one\ntwo\n")
        assertEquals("two", FileSourceLookup(listOf(dir)).lineText("com.acme.Foo\$Inner\$1", 2))
    }

    @Test
    fun `missing file and file-level class give null`() {
        source(dir, "com/acme/Foo.kt", "one\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertNull(lookup.lineText("com.acme.Other", 1))
        assertNull(lookup.lineText("com.acme.FooKt", 1))
    }

    @Test
    fun `out of range lines give null`() {
        source(dir, "com/acme/Foo.kt", "one\ntwo\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertNull(lookup.lineText("com.acme.Foo", 0))
        assertNull(lookup.lineText("com.acme.Foo", -1))
        assertNull(lookup.lineText("com.acme.Foo", 3))
    }

    @Test
    fun `first root that has the file wins and later roots are fallbacks`() {
        val first = Files.createDirectory(dir.resolve("a"))
        val second = Files.createDirectory(dir.resolve("b"))
        source(second, "com/acme/Foo.kt", "second")
        source(second, "com/acme/Bar.kt", "bar")
        val lookup = FileSourceLookup(listOf(first, second))
        assertEquals("second", lookup.lineText("com.acme.Foo", 1))
        source(first, "com/acme/Foo.kt", "first")
        assertEquals("first", lookup.lineText("com.acme.Foo", 1))
        assertEquals("bar", lookup.lineText("com.acme.Bar", 1))
    }

    @Test
    fun `malformed bytes are replaced instead of thrown`() {
        Files.write(
            dir.resolve("Foo.kt"),
            byteArrayOf('a'.code.toByte(), 0xC3.toByte(), '\n'.code.toByte(), 'b'.code.toByte()),
        )
        val lookup = FileSourceLookup(listOf(dir))
        assertEquals("a" + Char(0xFFFD), lookup.lineText("Foo", 1))
        assertEquals("b", lookup.lineText("Foo", 2))
    }

    @Test
    fun `class name cannot escape the root`() {
        val root = Files.createDirectory(dir.resolve("root"))
        source(dir, "Secret.kt", "secret")
        val outside = dir.resolve("Secret").toString()
        assumeTrue('.' !in outside)
        assertEquals("secret", FileSourceLookup(listOf(dir)).lineText(outside, 1))
        assertNull(FileSourceLookup(listOf(root)).lineText(outside, 1))
        assertNull(FileSourceLookup(listOf(root)).lineText("../Secret", 1))
    }

    @Test
    fun `no roots finds nothing`() {
        assertNull(FileSourceLookup(emptyList()).lineText("com.acme.Foo", 1))
    }

    @Test
    fun `class in the default package resolves`() {
        source(dir, "Foo.kt", "x")
        assertEquals("x", FileSourceLookup(listOf(dir)).lineText("Foo", 1))
    }
}
