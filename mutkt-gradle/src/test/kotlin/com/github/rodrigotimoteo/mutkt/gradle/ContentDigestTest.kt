package com.github.rodrigotimoteo.mutkt.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentDigestTest {
    @TempDir
    lateinit var dir: File

    private fun tree(
        name: String,
        files: Map<String, String>,
    ) = dir.resolve(name).also { root ->
        root.mkdirs()
        files.forEach { (path, text) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(text) }
    }

    @Test
    fun `a file digest follows its content, not its name`() {
        val a = dir.resolve("a.jar").apply { writeText("x") }
        val b = dir.resolve("b.jar").apply { writeText("x") }

        assertEquals(contentDigest(a.toPath()), contentDigest(b.toPath()))
        b.writeText("y")
        assertNotEquals(contentDigest(a.toPath()), contentDigest(b.toPath()))
    }

    @Test
    fun `a directory digest follows its files' paths and contents`() {
        val base = contentDigest(tree("a", mapOf("x/1.txt" to "1", "2.txt" to "2")).toPath())

        assertEquals(base, contentDigest(tree("b", mapOf("2.txt" to "2", "x/1.txt" to "1")).toPath()))
        assertNotEquals(base, contentDigest(tree("c", mapOf("x/1.txt" to "1", "2.txt" to "3")).toPath()))
        assertNotEquals(base, contentDigest(tree("d", mapOf("y/1.txt" to "1", "2.txt" to "2")).toPath()))
    }

    @Test
    fun `a missing entry has a digest of its own`() =
        assertNotEquals(contentDigest(tree("e", emptyMap()).toPath()), contentDigest(dir.resolve("none").toPath()))
}
