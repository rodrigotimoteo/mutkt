package com.github.rodrigotimoteo.mutkt.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        assertEquals("one", lookup.lineText("com.acme.Foo", "Foo.kt", 1))
        assertEquals("three", lookup.lineText("com.acme.Foo", "Foo.kt", 3))
    }

    @Test
    fun `finds a line in a java file`() {
        source(dir, "com/acme/Foo.java", "class Foo {}\n")
        assertEquals("class Foo {}", FileSourceLookup(listOf(dir)).lineText("com.acme.Foo", "Foo.java", 1))
    }

    @Test
    fun `the source file name decides, not the class name`() {
        source(dir, "com/acme/Prices.kt", "real")
        source(dir, "com/acme/PricesKt.kt", "decoy")
        val lookup = FileSourceLookup(listOf(dir))
        assertEquals("real", lookup.lineText("com.acme.PricesKt", "Prices.kt", 1))
        assertEquals("real", lookup.lineText("com.acme.Money", "Prices.kt", 1))
        assertEquals("real", lookup.lineText("com.acme.Prices\$Inner\$1", "Prices.kt", 1))
    }

    @Test
    fun `package directory wins over same-named files elsewhere`() {
        source(dir, "com/other/Foo.kt", "other")
        source(dir, "com/acme/Foo.kt", "acme")
        assertEquals("acme", FileSourceLookup(listOf(dir)).lineText("com.acme.Foo", "Foo.kt", 1))
    }

    @Test
    fun `file outside its package directory is found when its name is unique`() {
        source(dir, "src/misc/Foo.kt", "relocated")
        assertEquals("relocated", FileSourceLookup(listOf(dir)).lineText("com.acme.Foo", "Foo.kt", 1))
    }

    @Test
    fun `two same-named files outside the package directory find nothing`() {
        source(dir, "one/Foo.kt", "one")
        source(dir, "two/Foo.kt", "two")
        assertNull(FileSourceLookup(listOf(dir)).lineText("com.acme.Foo", "Foo.kt", 1))
    }

    @Test
    fun `a missing root is skipped`() {
        source(dir, "misc/Foo.kt", "x")
        assertEquals("x", FileSourceLookup(listOf(dir.resolve("none"), dir)).lineText("a.Foo", "Foo.kt", 1))
    }

    @Test
    fun `no source file name or a missing file gives null`() {
        source(dir, "com/acme/Foo.kt", "one\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertNull(lookup.lineText("com.acme.Foo", null, 1))
        assertNull(lookup.lineText("com.acme.Foo", "Other.kt", 1))
    }

    @Test
    fun `out of range lines give null`() {
        source(dir, "com/acme/Foo.kt", "one\ntwo\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertNull(lookup.lineText("com.acme.Foo", "Foo.kt", 0))
        assertNull(lookup.lineText("com.acme.Foo", "Foo.kt", -1))
        assertNull(lookup.lineText("com.acme.Foo", "Foo.kt", 3))
    }

    @Test
    fun `first root that has the file wins and later roots are fallbacks`() {
        val first = Files.createDirectory(dir.resolve("a"))
        val second = Files.createDirectory(dir.resolve("b"))
        source(second, "com/acme/Foo.kt", "second")
        source(second, "com/acme/Bar.kt", "bar")
        val lookup = FileSourceLookup(listOf(first, second))
        assertEquals("second", lookup.lineText("com.acme.Foo", "Foo.kt", 1))
        source(first, "com/acme/Foo.kt", "first")
        assertEquals("first", lookup.lineText("com.acme.Foo", "Foo.kt", 1))
        assertEquals("bar", lookup.lineText("com.acme.Bar", "Bar.kt", 1))
    }

    @Test
    fun `malformed bytes are replaced instead of thrown`() {
        Files.write(
            dir.resolve("Foo.kt"),
            byteArrayOf('a'.code.toByte(), 0xC3.toByte(), '\n'.code.toByte(), 'b'.code.toByte()),
        )
        val lookup = FileSourceLookup(listOf(dir))
        assertEquals("a" + Char(0xFFFD), lookup.lineText("Foo", "Foo.kt", 1))
        assertEquals("b", lookup.lineText("Foo", "Foo.kt", 2))
    }

    @Test
    fun `class and file names cannot escape the root`() {
        val root = Files.createDirectory(dir.resolve("root"))
        source(dir, "Secret.kt", "secret")
        source(dir, "x/Secret.kt", "secret")
        val lookup = FileSourceLookup(listOf(root))
        assertNull(lookup.lineText("Foo", "../Secret.kt", 1))
        assertNull(lookup.lineText("../x.Foo", "Secret.kt", 1))
    }

    @Test
    fun `no roots finds nothing`() {
        assertNull(FileSourceLookup(emptyList()).lineText("com.acme.Foo", "Foo.kt", 1))
    }

    @Test
    fun `source text joins the lines with newlines whatever the file used`() {
        source(dir, "com/acme/Foo.kt", "one\r\ntwo\n\nfour\n")
        val lookup = FileSourceLookup(listOf(dir))
        assertEquals("one\ntwo\n\nfour", lookup.sourceText("com.acme.Foo", "Foo.kt"))
    }

    @Test
    fun `source text is null without a file name or file`() {
        source(dir, "com/acme/Foo.kt", "one")
        val lookup = FileSourceLookup(listOf(dir))
        assertNull(lookup.sourceText("com.acme.Foo", null))
        assertNull(lookup.sourceText("com.acme.Foo", "Other.kt"))
    }

    @Test
    fun `class in the default package resolves`() {
        source(dir, "Foo.kt", "x")
        assertEquals("x", FileSourceLookup(listOf(dir)).lineText("Foo", "Foo.kt", 1))
    }
}
