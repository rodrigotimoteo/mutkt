package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.report.FileSourceLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RepositoryPathsTest {
    @TempDir
    lateinit var dir: Path

    private fun mutant(sourceFile: String?): Mutant {
        val op = OperatorId("arithmetic")
        return Mutant(MutantId(op, "demo.Calc", "add", "(II)I", 0), op, "demo.Calc", "add", "(II)I", 3, "x", sourceFile)
    }

    private fun sources(relative: String): Path =
        dir.resolve(relative).also {
            Files.createDirectories(it.resolve("demo"))
            Files.writeString(it.resolve("demo/Calc.kt"), "package demo\n")
        }

    @Test
    fun `a source file under the root is named relative to it with forward slashes`() {
        val paths = repositoryPaths(FileSourceLookup(listOf(sources("app/src/main/kotlin"))), dir.resolve("."))

        assertEquals("app/src/main/kotlin/demo/Calc.kt", paths(mutant("Calc.kt")))
    }

    @Test
    fun `a root reached through a symlink still contains its sources`() {
        val lookup = FileSourceLookup(listOf(sources("repo/src")))
        val link = Files.createSymbolicLink(dir.resolve("link"), dir.resolve("repo"))

        assertEquals("src/demo/Calc.kt", repositoryPaths(lookup, link)(mutant("Calc.kt")))
    }

    @Test
    fun `a file outside the root, a missing file and a class without a source file have no path`() {
        val paths = repositoryPaths(FileSourceLookup(listOf(sources("elsewhere"))), dir.resolve("repo"))

        assertNull(paths(mutant("Calc.kt")))
        assertNull(paths(mutant("Gone.kt")))
        assertNull(paths(mutant(null)))
    }
}
