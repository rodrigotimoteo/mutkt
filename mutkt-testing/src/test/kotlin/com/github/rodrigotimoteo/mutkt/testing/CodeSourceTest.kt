package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CodeSourceTest {
    @Test
    fun `library class resolves to its jar`() {
        val path = codeSourceOf(Test::class.java)

        assertTrue(Files.isRegularFile(path))
        assertTrue(path.fileName.toString().startsWith("junit-jupiter-api-"), path.toString())
    }

    @Test
    fun `class compiled in this module resolves to its output directory`() {
        val path = codeSourceOf(CodeSourceTest::class.java)

        assertTrue(Files.isDirectory(path))
        assertTrue(Files.isRegularFile(path.resolve(CodeSourceTest::class.java.name.replace('.', '/') + ".class")))
    }
}
