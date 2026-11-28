package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonFileHistoryTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("mutkt/history.json")

    private val history =
        History(
            "f1",
            listOf(
                HistoryEntry(
                    "negate:demo.Calc#add(II)I:0",
                    "k1",
                    MutantStatus.KILLED,
                    "demo.CalcTest",
                    KillKind.ASSERTION,
                ),
                HistoryEntry("math:demo.Calc#\$lambda<init>(II)I:1", "k2", MutantStatus.SURVIVED),
            ),
        )

    @Test
    fun `reads back what it wrote, creating the directory`() {
        JsonFileHistory(file).write(history)

        assertEquals(history, JsonFileHistory(file).read())
    }

    @Test
    fun `a missing file is no history`() = assertNull(JsonFileHistory(file).read())

    @Test
    fun `a file of another format version is no history`() {
        file.parentFile.mkdirs()
        file.writeText("""{"version": 1, "fingerprint": "f", "mutants": []}""")

        assertNull(JsonFileHistory(file).read())
    }

    @Test
    fun `a corrupt file fails with its path and how to recover`() {
        file.parentFile.mkdirs()
        val noFiles = """{"version": 2, "fingerprint": "f", "mutants": [], "files": []}"""
        for (text in listOf("{not json", "", "[1, 2]", """{"version": 2, "fingerprint": "f"}""", noFiles)) {
            file.writeText(text)

            val error = assertThrows<GradleException>(text) { JsonFileHistory(file).read() }

            val message = error.message.orEmpty()
            assertTrue(message.startsWith("The MutKt result history ${file.path} is corrupt"), message)
            assertTrue(message.endsWith("delete it and run again"), message)
        }
    }

    @Test
    fun `an unreadable file fails with its path and how to recover`() {
        file.mkdirs()

        val message = assertThrows<GradleException> { JsonFileHistory(file).read() }.message.orEmpty()

        assertTrue(message.startsWith("The MutKt result history ${file.path} cannot be read"), message)
        assertTrue(message.endsWith("delete it and run again"), message)
    }

    @Test
    fun `reuses a file digest while the file's size and modification time hold`() {
        val jar = dir.resolve("a.jar").apply { writeText("one") }
        val first = JsonFileHistory(file)
        val digest = first.digests.digest(jar.toPath())
        first.write(history)
        val time = jar.lastModified()

        // Same size and time: the stored digest is trusted, so the new content is not read.
        jar.writeText("two")
        jar.setLastModified(time)
        assertEquals(digest, JsonFileHistory(file).digests.digest(jar.toPath()))
        jar.writeText("three")
        assertNotEquals(digest, JsonFileHistory(file).digests.digest(jar.toPath()))
    }
}
