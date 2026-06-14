package com.github.rodrigotimoteo.mutation.baseline

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BaselineStorageTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var baseline: BaselineStorage

    @BeforeEach
    fun setUp() {
        baseline = BaselineStorage(tempDir)
    }

    @Test
    fun `save and load work correctly`() {
        val data =
            mapOf(
                "com.example.Foo" to
                    listOf(
                        Triple("ARITHMETIC", 42, MutationStatus.KILLED),
                        Triple("RETURN_VALS", 43, MutationStatus.SURVIVED),
                    ),
            )

        baseline.save(data)
        val loaded = baseline.load()

        assertEquals(1, loaded.size)
        assertEquals(2, loaded["com.example.Foo"]?.size)
    }

    @Test
    fun `load returns empty map when no baseline exists`() {
        val loaded = baseline.load()
        assertTrue(loaded.isEmpty(), "expected empty map when no baseline file, got: $loaded")
    }

    @Test
    fun `save overwrites existing baseline`() {
        val data1 =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )
        val data2 =
            mapOf(
                "com.example.Bar" to listOf(Triple("RETURN_VALS", 43, MutationStatus.SURVIVED)),
            )

        baseline.save(data1)
        baseline.save(data2)

        val loaded = baseline.load()
        assertEquals(1, loaded.size)
        assertTrue(loaded.containsKey("com.example.Bar"), "expected com.example.Bar after overwrite, got: ${loaded.keys}")
    }

    @Test
    fun `compareWithBaseline detects new mutations`() {
        val baselineData =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )
        baseline.save(baselineData)

        val currentResults =
            mapOf(
                "com.example.Foo" to
                    listOf(
                        Triple("ARITHMETIC", 42, MutationStatus.KILLED),
                        Triple("RETURN_VALS", 43, MutationStatus.SURVIVED),
                    ),
            )

        val diff = baseline.compareWithBaseline(currentResults)
        assertTrue(diff.hasChanges)
        assertEquals(1, diff.newMutations.size)
        assertTrue(diff.newMutations.any { it.contains("RETURN_VALS") })
    }

    @Test
    fun `compareWithBaseline detects changed status`() {
        val baselineData =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )
        baseline.save(baselineData)

        val currentResults =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.SURVIVED)),
            )

        val diff = baseline.compareWithBaseline(currentResults)
        assertTrue(diff.hasChanges)
        assertEquals(1, diff.changedStatus.size)
    }

    @Test
    fun `compareWithBaseline detects removed mutations`() {
        val baselineData =
            mapOf(
                "com.example.Foo" to
                    listOf(
                        Triple("ARITHMETIC", 42, MutationStatus.KILLED),
                        Triple("RETURN_VALS", 43, MutationStatus.SURVIVED),
                    ),
            )
        baseline.save(baselineData)

        val currentResults =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )

        val diff = baseline.compareWithBaseline(currentResults)
        assertTrue(diff.hasChanges)
        assertEquals(1, diff.removedMutations.size)
    }

    @Test
    fun `compareWithBaseline returns no changes when identical`() {
        val data =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )
        baseline.save(data)

        val diff = baseline.compareWithBaseline(data)
        assertFalse(diff.hasChanges)
    }

    @Test
    fun `clear removes baseline file`() {
        val data =
            mapOf(
                "com.example.Foo" to listOf(Triple("ARITHMETIC", 42, MutationStatus.KILLED)),
            )
        baseline.save(data)
        baseline.clear()

        val loaded = baseline.load()
        assertTrue(loaded.isEmpty(), "expected empty map after clear, got: $loaded")
    }

    @Test
    fun `detectChanges returns empty set when not in git repo`() {
        // tempDir is not a git repo
        val changes = baseline.detectChanges()
        assertTrue(changes.isEmpty(), "expected no git changes outside repo, got: $changes")
    }

    @Test
    fun `DiffResult summary formats correctly`() {
        val diff =
            DiffResult(
                newMutations = listOf("a", "b"),
                changedStatus = listOf(Triple("c", "d", MutationStatus.KILLED)),
                removedMutations = emptyList(),
            )

        assertEquals("New: 2, Changed: 1, Removed: 0", diff.summary)
    }
}
