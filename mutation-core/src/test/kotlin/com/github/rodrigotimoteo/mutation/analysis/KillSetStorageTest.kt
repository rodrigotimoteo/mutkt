package com.github.rodrigotimoteo.mutation.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KillSetStorageTest {
    companion object {
        @TempDir
        @JvmStatic
        lateinit var tempDir: File
    }

    @BeforeEach
    fun setUp() {
        // Clean up any leftover kill-sets file from previous tests
        File(tempDir, ".mutkt/kill-sets.txt").delete()
    }

    private fun createStorage() = KillSetStorage(tempDir)

    @Test
    fun `load returns empty map when no file exists`() {
        val storage = createStorage()
        val result = storage.load()
        assertTrue(result.isEmpty(), "expected empty map when no file exists, got: $result")
    }

    @Test
    fun `save and load roundtrip`() {
        val storage = createStorage()
        val killSets =
            mapOf(
                "ARITHMETIC::com.example.Calc::add::10" to setOf("CalcTest", "MathTest"),
                "RETURN_VALUE::com.example.Calc::add::15" to setOf("CalcTest"),
            )

        storage.save(killSets)
        val loaded = storage.load()

        assertEquals(killSets, loaded)
    }

    @Test
    fun `save creates mutkt directory`() {
        val storage = createStorage()
        val killSets = mapOf("test" to setOf("Test1"))
        storage.save(killSets)

        val mutktDir = File(tempDir, ".mutkt")
        assertTrue(mutktDir.exists(), "expected .mutkt directory to be created at ${mutktDir.absolutePath}")
        assertTrue(File(mutktDir, "kill-sets.txt").exists(), "expected kill-sets.txt to be created")
    }

    @Test
    fun `load handles corrupted file gracefully`() {
        val storage = createStorage()
        val killSetsFile = File(tempDir, ".mutkt/kill-sets.txt")
        killSetsFile.parentFile.mkdirs()
        killSetsFile.writeText("not valid data without equals sign\n")

        val result = storage.load()
        assertTrue(result.isEmpty(), "expected empty map for corrupted file, got: $result")
    }

    @Test
    fun `merge prefers new data over historical`() {
        val storage = createStorage()
        val historical =
            mapOf(
                "M1" to setOf("Test1"),
                "M2" to setOf("Test2"),
            )
        val newKillSets =
            mapOf(
                "M1" to setOf("Test1", "Test3"),
                "M3" to setOf("Test4"),
            )

        val merged = storage.merge(historical, newKillSets)

        assertEquals(setOf("Test1", "Test3"), merged["M1"], "M1 should be unioned from historical and new")
        assertEquals(setOf("Test2"), merged["M2"], "M2 should come from historical")
        assertEquals(setOf("Test4"), merged["M3"], "M3 should come from new")
    }

    @Test
    fun `load handles empty file`() {
        val storage = createStorage()
        val killSetsFile = File(tempDir, ".mutkt/kill-sets.txt")
        killSetsFile.parentFile.mkdirs()
        killSetsFile.writeText("")

        val result = storage.load()
        assertTrue(result.isEmpty(), "expected empty map for empty file, got: $result")
    }

    @Test
    fun `load handles mutation with no killing tests`() {
        val storage = createStorage()
        val killSetsFile = File(tempDir, ".mutkt/kill-sets.txt")
        killSetsFile.parentFile.mkdirs()
        killSetsFile.writeText("M1=\nM2=Test1,Test2\n")

        val result = storage.load()
        assertEquals(emptySet<String>(), result["M1"], "M1 should have empty kill set")
        assertEquals(setOf("Test1", "Test2"), result["M2"], "M2 should have two killing tests")
    }
}
