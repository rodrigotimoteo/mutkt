package com.github.rodrigotimoteo.mutation.android

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AarExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts classes jar from valid aar`() {
        val aar = createFakeAar(tempDir.resolve("test.aar"), includeClassesJar = true)
        val extractor = AarExtractor(tempDir.toFile())
        val extracted = extractor.extractClassesJar(aar.toFile())
        assertTrue(extracted.exists())
        assertEquals("classes.jar", extracted.name)
    }

    @Test
    fun `throws when aar has no classes jar`() {
        val aar = createFakeAar(tempDir.resolve("no-jar.aar"), includeClassesJar = false)
        val extractor = AarExtractor(tempDir.toFile())
        assertThrows<IllegalStateException> {
            extractor.extractClassesJar(aar.toFile())
        }
    }

    @Test
    fun `throws when file is not aar`() {
        val notAar = tempDir.resolve("not-aar.txt").toFile()
        notAar.writeText("hello")
        val extractor = AarExtractor(tempDir.toFile())
        assertThrows<IllegalArgumentException> {
            extractor.extractClassesJar(notAar)
        }
    }

    private fun createFakeAar(
        path: Path,
        includeClassesJar: Boolean,
    ): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            if (includeClassesJar) {
                zip.putNextEntry(ZipEntry("classes.jar"))
                zip.write("fake-classes".toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest/>".toByteArray())
            zip.closeEntry()
        }
        return path
    }
}
