package com.github.rodrigotimoteo.mutkt.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ClassStagingTest {
    @TempDir
    lateinit var dir: File

    // Staging only copies files, so the bytes need not be a class.
    private fun classFile(
        root: String,
        path: String,
    ) = File(dir, "$root/$path").apply {
        parentFile.mkdirs()
        writeBytes(byteArrayOf(1))
    }

    @Test
    fun `merges the roots and records where each class came from`() {
        classFile("kotlin", "a/B.class")
        classFile("kotlin", "a/B\$1.class")
        classFile("java", "C.class")
        val kotlin = File(dir, "kotlin")
        val java = File(dir, "java")

        val staged = stage(setOf(kotlin, java, File(dir, "missing")), File(dir, "staged"))

        assertTrue(Files.isRegularFile(staged.dir.resolve("a/B.class")))
        assertTrue(Files.isRegularFile(staged.dir.resolve("C.class")))
        assertEquals(
            mapOf("a.B" to kotlin.toPath(), "a.B\$1" to kotlin.toPath(), "C" to java.toPath()),
            staged.origins,
        )
    }

    @Test
    fun `a class unpacked from a jar comes from the jar`() {
        val jar = File(dir, "classes.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("d/E.class"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }

        val staged = stage(setOf(jar), File(dir, "staged"))

        assertTrue(Files.isRegularFile(staged.dir.resolve("d/E.class")))
        assertEquals(mapOf("d.E" to jar.toPath()), staged.origins)
    }
}
