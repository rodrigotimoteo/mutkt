package com.github.rodrigotimoteo.mutation.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IncrementalAnalyzerTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var analyzer: IncrementalAnalyzer

    @BeforeEach
    fun setup() {
        analyzer = IncrementalAnalyzer(tempDir)
    }

    private fun gitInit() {
        ProcessBuilder("git", "init")
            .directory(tempDir)
            .start().waitFor()
        ProcessBuilder("git", "config", "user.email", "test@test.com")
            .directory(tempDir)
            .start().waitFor()
        ProcessBuilder("git", "config", "user.name", "Test")
            .directory(tempDir)
            .start().waitFor()
    }

    private fun gitCommit(message: String) {
        ProcessBuilder("git", "add", "-A")
            .directory(tempDir)
            .start().waitFor()
        ProcessBuilder("git", "commit", "-m", message)
            .directory(tempDir)
            .start().waitFor()
    }

    @Test
    fun `isAvailable returns true in git repo`() {
        gitInit()
        assertTrue(analyzer.isAvailable())
    }

    @Test
    fun `isAvailable returns false in non-git dir`() {
        assertFalse(analyzer.isAvailable())
    }

    @Test
    fun `getChangedClasses returns empty when no changes`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "MyClass.kt").writeText("class MyClass")
        gitCommit("initial")

        val result = analyzer.getChangedClasses()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangedClasses detects modified Kotlin file`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "MyClass.kt").writeText("class MyClass")
        gitCommit("initial")

        File(srcDir, "MyClass.kt").writeText("class MyClass { fun foo() = 42 }")
        val result = analyzer.getChangedClasses()
        assertEquals(setOf("com.example.MyClass"), result)
    }

    @Test
    fun `getChangedClasses detects new Java file`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/java/com/example")
        srcDir.mkdirs()
        File(srcDir, "Existing.java").writeText("class Existing {}")
        gitCommit("initial")

        File(srcDir, "Helper.java").writeText("class Helper {}")
        ProcessBuilder("git", "add", "-A").directory(tempDir).start().waitFor()
        val result = analyzer.getChangedClasses()
        assertEquals(setOf("com.example.Helper"), result)
    }

    @Test
    fun `getChangedClasses maps multiple files`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "Foo.kt").writeText("class Foo")
        File(srcDir, "Bar.kt").writeText("class Bar")
        gitCommit("initial")

        File(srcDir, "Foo.kt").writeText("class Foo { fun x() = 1 }")
        File(srcDir, "Bar.kt").writeText("class Bar { fun y() = 2 }")
        val result = analyzer.getChangedClasses()
        assertEquals(setOf("com.example.Foo", "com.example.Bar"), result)
    }

    @Test
    fun `getChangedClasses ignores non-kt-java files`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "Keep.kt").writeText("class Keep")
        gitCommit("initial")

        File(tempDir, "README.md").writeText("# hello")
        val result = analyzer.getChangedClasses()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangedClasses with custom sourceDirs`() {
        gitInit()
        val srcDir = File(tempDir, "src/custom/com/example")
        srcDir.mkdirs()
        File(srcDir, "Existing.kt").writeText("class Existing")
        gitCommit("initial")

        File(srcDir, "Custom.kt").writeText("class Custom")
        ProcessBuilder("git", "add", "-A").directory(tempDir).start().waitFor()
        val result = analyzer.getChangedClasses(sourceDirs = listOf("src/custom"))
        assertEquals(setOf("com.example.Custom"), result)
    }

    @Test
    fun `getChangedClasses ignores files not matching any sourceDir`() {
        gitInit()
        gitCommit("initial empty")

        val srcDir = File(tempDir, "test/com/example")
        srcDir.mkdirs()
        File(srcDir, "Test.kt").writeText("class Test")
        val result = analyzer.getChangedClasses()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangedClasses does not include deleted files`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "DeleteMe.kt").writeText("class DeleteMe")
        gitCommit("add file")

        File(srcDir, "DeleteMe.kt").delete()
        val result = analyzer.getChangedClasses()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangedClasses returns empty when git fails`() {
        val nonGitDir = File(tempDir, "not-a-repo")
        nonGitDir.mkdirs()
        val analyzer2 = IncrementalAnalyzer(nonGitDir)
        val result = analyzer2.getChangedClasses()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangedClasses with baseBranch when no origin`() {
        gitInit()
        val srcDir = File(tempDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "Foo.kt").writeText("class Foo")
        gitCommit("initial")
        // No origin configured, so origin/main...HEAD fails
        val result = analyzer.getChangedClasses(baseBranch = "main")
        assertTrue(result.isEmpty())
    }
}
