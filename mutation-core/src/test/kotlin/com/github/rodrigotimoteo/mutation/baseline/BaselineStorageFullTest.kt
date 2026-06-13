package com.github.rodrigotimoteo.mutation.baseline

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BaselineStorageFullTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var baseline: BaselineStorage

    @BeforeEach
    fun setUp() {
        baseline = BaselineStorage(tempDir)
    }

    @Test
    fun `load skips blank lines`() {
        File(tempDir, ".mutkt/baseline").writeText(
            """
            com.example.Foo|ARITHMETIC|42|KILLED

            com.example.Bar|RETURN_VALS|10|SURVIVED

            """.trimIndent(),
        )

        val loaded = baseline.load()

        assertEquals(2, loaded.size)
        assertEquals(1, loaded["com.example.Foo"]?.size)
        assertEquals(1, loaded["com.example.Bar"]?.size)
    }

    @Test
    fun `load skips lines with wrong field count`() {
        File(tempDir, ".mutkt/baseline").writeText(
            """
            com.example.Foo|ARITHMETIC|42|KILLED
            com.example.Bar|RETURN_VALS|10
            com.example.Baz|RETURN_VALS|10|SURVIVED|extra
            com.example.Qux|VOID|5|SURVIVED
            """.trimIndent(),
        )

        val loaded = baseline.load()

        assertEquals(2, loaded.size)
        assertTrue(loaded.containsKey("com.example.Foo"))
        assertTrue(loaded.containsKey("com.example.Qux"))
        assertFalse(loaded.containsKey("com.example.Bar"))
        assertFalse(loaded.containsKey("com.example.Baz"))
    }

    @Test
    fun `load skips lines with invalid line number`() {
        File(tempDir, ".mutkt/baseline").writeText(
            """
            com.example.Foo|ARITHMETIC|notanumber|KILLED
            com.example.Bar|RETURN_VALS|10|SURVIVED
            """.trimIndent(),
        )

        val loaded = baseline.load()

        assertEquals(1, loaded.size)
        assertTrue(loaded.containsKey("com.example.Bar"))
    }

    @Test
    fun `load skips lines with invalid status`() {
        File(tempDir, ".mutkt/baseline").writeText(
            """
            com.example.Foo|ARITHMETIC|42|NOT_A_REAL_STATUS
            com.example.Bar|RETURN_VALS|10|SURVIVED
            """.trimIndent(),
        )

        val loaded = baseline.load()

        assertEquals(1, loaded.size)
        assertTrue(loaded.containsKey("com.example.Bar"))
    }

    @Test
    fun `saveMerged merges new classes without deleting existing baseline classes`() {
        baseline.save(
            mapOf(
                "com.example.Old" to listOf(Triple("ARITHMETIC", 1, MutationStatus.KILLED)),
            ),
        )

        baseline.saveMerged(
            mapOf(
                "com.example.New" to listOf(Triple("RETURN_VALS", 5, MutationStatus.SURVIVED)),
            ),
        )

        val loaded = baseline.load()
        assertEquals(2, loaded.size)
        assertTrue(loaded.containsKey("com.example.Old"))
        assertTrue(loaded.containsKey("com.example.New"))
        assertEquals(1, loaded["com.example.Old"]?.size)
        assertEquals(1, loaded["com.example.New"]?.size)
    }

    @Test
    fun `detectChanges returns changed Kotlin class in git repo`() {
        val repoDir = tempDir
        runGit(repoDir, "init")
        runGit(repoDir, "config", "user.email", "test@example.com")
        runGit(repoDir, "config", "user.name", "Tester")
        File(repoDir, "src/main/kotlin/com/example/Foo.kt").apply {
            parentFile.mkdirs()
            writeText("package com.example\nclass Foo\n")
        }
        runGit(repoDir, "add", ".")
        runGit(repoDir, "commit", "-m", "init")

        File(repoDir, "src/main/kotlin/com/example/Foo.kt").appendText("// changed")
        runGit(repoDir, "add", "src/main/kotlin/com/example/Foo.kt")

        val changes = baseline.detectChanges()

        assertTrue(changes.isNotEmpty(), "expected changed classes, got empty set")
        assertTrue(
            changes.any { it.contains("Foo") },
            "expected Foo class in changes, got $changes",
        )
    }

    @Test
    fun `detectChanges filters out non-source files`() {
        val repoDir = tempDir
        runGit(repoDir, "init")
        runGit(repoDir, "config", "user.email", "test@example.com")
        runGit(repoDir, "config", "user.name", "Tester")
        File(repoDir, "README.md").writeText("hi")
        runGit(repoDir, "add", ".")
        runGit(repoDir, "commit", "-m", "init")

        File(repoDir, "README.md").appendText(" more")
        File(repoDir, "src/main/kotlin/com/example/Foo.kt").apply {
            parentFile.mkdirs()
            writeText("package com.example\nclass Foo\n")
        }
        runGit(repoDir, "add", "README.md")
        runGit(repoDir, "commit", "-m", "readme change")

        val changes = baseline.detectChanges()

        assertTrue(changes.none { it.contains("README") })
    }

    @Test
    fun `compareWithBaseline reports entirely new class as new mutations`() {
        baseline.save(
            mapOf(
                "com.example.Old" to listOf(Triple("ARITHMETIC", 1, MutationStatus.KILLED)),
            ),
        )

        val currentResults =
            mapOf(
                "com.example.New" to
                    listOf(
                        Triple("ARITHMETIC", 10, MutationStatus.SURVIVED),
                        Triple("RETURN_VALS", 20, MutationStatus.SURVIVED),
                    ),
            )

        val diff = baseline.compareWithBaseline(currentResults)

        assertTrue(diff.hasChanges)
        assertEquals(2, diff.newMutations.size)
        assertTrue(diff.newMutations.all { it.startsWith("com.example.New:") })
    }

    @Test
    fun `compareWithBaseline reports removed class as removed mutations`() {
        baseline.save(
            mapOf(
                "com.example.Removed" to
                    listOf(
                        Triple("ARITHMETIC", 1, MutationStatus.KILLED),
                        Triple("RETURN_VALS", 2, MutationStatus.SURVIVED),
                    ),
            ),
        )

        val currentResults =
            mapOf(
                "com.example.Other" to listOf(Triple("ARITHMETIC", 1, MutationStatus.KILLED)),
            )

        val diff = baseline.compareWithBaseline(currentResults)

        assertTrue(diff.hasChanges)
        assertEquals(2, diff.removedMutations.size)
        assertTrue(diff.removedMutations.all { it.startsWith("com.example.Removed:") })
    }

    private fun runGit(
        dir: File,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder("git", *args)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed with exit $exit" }
    }
}
