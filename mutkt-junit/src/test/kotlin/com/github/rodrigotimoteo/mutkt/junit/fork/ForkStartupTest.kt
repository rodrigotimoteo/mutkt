package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.junit.CALC
import com.github.rodrigotimoteo.mutkt.junit.splitClasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ForkStartupTest {
    @TempDir
    lateinit var dir: Path

    private fun parse(vararg lines: String) = ForkStartup.parse(Files.write(dir.resolve("startup"), lines.toList()))

    @Test
    fun `reads the directories and every classpath entry in order`() {
        val startup =
            parse("projectClassDir=/p", "testClassDir=/t", "", "classpath=/a.jar", "classpath=/b=c.jar", "engine=e")
        assertEquals(
            ForkStartup(Path.of("/p"), Path.of("/t"), listOf(Path.of("/a.jar"), Path.of("/b=c.jar")), listOf("e")),
            startup,
        )
    }

    @Test
    fun `reads engines and tags in order, keeping tag expressions intact`() {
        val startup =
            parse(
                "projectClassDir=/p",
                "testClassDir=/t",
                "engine=junit-jupiter",
                "engine=junit-vintage",
                "includeTag=fast",
                "includeTag=a & b",
                "excludeTag=!slow",
            )
        assertEquals(listOf("junit-jupiter", "junit-vintage"), startup.engines)
        assertEquals(listOf("fast", "a & b"), startup.includeTags)
        assertEquals(listOf("!slow"), startup.excludeTags)
    }

    @Test
    fun `rejects unknown keys, missing keys and malformed lines`() {
        assertThrows<IllegalArgumentException> {
            parse("projectClassDir=/p", "testClassDir=/t", "engine=e", "other=1")
        }
        assertThrows<IllegalArgumentException> { parse("projectClassDir=/p", "testClassDir=/t") }
        assertThrows<IllegalArgumentException> { parse("projectClassDir=/p") }
        assertThrows<IllegalArgumentException> {
            parse("projectClassDir=/p", "projectClassDir=/q", "testClassDir=/t", "engine=e")
        }
        assertThrows<IllegalArgumentException> { parse("projectClassDir") }
    }

    @Test
    fun `class files are named by binary name from nested directories`() {
        val calc = splitClasses(CALC).first.single().bytes
        Files.createDirectories(dir.resolve("a/b"))
        Files.write(dir.resolve("a/b/Calc.class"), calc)
        Files.write(dir.resolve("notes.txt"), byteArrayOf(1))
        assertEquals(listOf("a.b.Calc"), readClassFiles(dir).map { it.name })
    }
}
