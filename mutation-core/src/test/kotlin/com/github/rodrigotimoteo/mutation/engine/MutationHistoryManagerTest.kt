package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationHistoryManagerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load on non-existent file returns empty history`() {
        val nonexistent = File(tempDir.toFile(), "does-not-exist")
        val manager = MutationHistoryManager(nonexistent)
        val history = manager.load()
        assertEquals(0, history.results.size)
        assertEquals(0, history.classHashes.size)
        assertEquals(0, history.testHashes.size)
    }

    @Test
    fun `save then load round-trip preserves data`() {
        val file = File(tempDir.toFile(), "history.txt")
        val manager = MutationHistoryManager(file)

        val history =
            MutationHistoryManager.MutationHistory(
                timestamp = 1234567890L,
                classHashes = mapOf("com.Foo" to "abc123"),
                testHashes = mapOf("com.FooTest" to "def456"),
                results =
                    mapOf(
                        "com.Foo_test1" to
                            MutationHistoryManager.MutationStatusResult(
                                status = MutationStatus.KILLED,
                                timestamp = 100L,
                                executionTimeMs = 42,
                            ),
                    ),
            )

        manager.save(history)
        val loaded = manager.load()

        assertEquals(history.timestamp, loaded.timestamp)
        assertEquals(history.classHashes, loaded.classHashes)
        assertEquals(history.testHashes, loaded.testHashes)
        assertEquals(history.results.size, loaded.results.size)
        val loadedResult = loaded.results["com.Foo_test1"]!!
        assertEquals(MutationStatus.KILLED, loadedResult.status)
        assertEquals(100L, loadedResult.timestamp)
        assertEquals(42, loadedResult.executionTimeMs)
    }

    @Test
    fun `save empty history produces valid format`() {
        val file = File(tempDir.toFile(), "history.txt")
        val manager = MutationHistoryManager(file)
        val empty = MutationHistoryManager.MutationHistory()
        manager.save(empty)

        val content = file.readText()
        assertTrue(content.contains("TIMESTAMP:"))
        assertTrue(file.exists())
    }

    @Test
    fun `parseHistory on corrupted hex line skips the line`() {
        val file = File(tempDir.toFile(), "history.txt")
        file.writeText(
            """
            TIMESTAMP:1000
            CLASS:Foo:validhash
            RESULT:Foo_m1:KILLED:200:50
            GARBAGE:this is not valid
            RESULT:Foo_m2:SURVIVED:300:60
            """.trimIndent(),
        )

        val manager = MutationHistoryManager(file)
        val history = manager.load()

        assertEquals(1, history.classHashes.size)
        assertEquals(2, history.results.size)
        assertEquals(MutationStatus.KILLED, history.results["Foo_m1"]?.status)
        assertEquals(MutationStatus.SURVIVED, history.results["Foo_m2"]?.status)
    }

    @Test
    fun `parseHistory on missing field uses defaults`() {
        val file = File(tempDir.toFile(), "history.txt")
        // Only mutationId and status, missing timestamp and executionTimeMs
        file.writeText(
            """
            TIMESTAMP:1000
            RESULT:Foo_m1:KILLED
            """.trimIndent(),
        )

        val manager = MutationHistoryManager(file)
        val history = manager.load()

        val result = history.results["Foo_m1"]!!
        assertEquals(MutationStatus.KILLED, result.status)
        // timestamp defaults to current time (not 0, the code uses System.currentTimeMillis())
        assertTrue(result.timestamp > 0)
        assertEquals(0, result.executionTimeMs)
    }

    @Test
    fun `parseHistory on invalid status defaults to ERROR`() {
        val file = File(tempDir.toFile(), "history.txt")
        file.writeText(
            """
            TIMESTAMP:1000
            RESULT:Foo_m1:INVALID_STATUS:200:50
            """.trimIndent(),
        )

        val manager = MutationHistoryManager(file)
        val history = manager.load()

        val result = history.results["Foo_m1"]!!
        assertEquals(MutationStatus.ERROR, result.status)
    }

    @Test
    fun `computeHash is deterministic`() {
        val manager = MutationHistoryManager(File(tempDir.toFile(), "dummy"))
        val bytes = "hello world".toByteArray()
        val hash1 = manager.computeHash(bytes)
        val hash2 = manager.computeHash(bytes)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeHash produces different hashes for different inputs`() {
        val manager = MutationHistoryManager(File(tempDir.toFile(), "dummy"))
        val hash1 = manager.computeHash("hello".toByteArray())
        val hash2 = manager.computeHash("world".toByteArray())
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `getReusableResults returns matching results when hashes match`() {
        val manager = MutationHistoryManager(File(tempDir.toFile(), "dummy"))
        val classBytes = "classA".toByteArray()
        val testBytes = "testA".toByteArray()
        val classHash = manager.computeHash(classBytes)
        val testHash = manager.computeHash(testBytes)

        // Use real mutation ID format: ${operator}_${className}_${methodName}_${line}
        val mutationId = "ARITHMETIC_com.Foo_test1_10"

        val oldHistory =
            MutationHistoryManager.MutationHistory(
                classHashes = mapOf("com.Foo" to classHash),
                testHashes = mapOf("com.Foo" to testHash),
                results =
                    mapOf(
                        mutationId to
                            MutationHistoryManager.MutationStatusResult(
                                status = MutationStatus.KILLED,
                            ),
                    ),
            )

        val reusable = manager.getReusableResults(classBytes, testBytes, oldHistory)
        assertEquals(1, reusable.size)
        assertEquals(MutationStatus.KILLED, reusable[mutationId])
    }

    @Test
    fun `getReusableResults returns empty when hashes differ`() {
        val manager = MutationHistoryManager(File(tempDir.toFile(), "dummy"))
        val classBytes = "classA".toByteArray()
        val testBytes = "testA".toByteArray()
        val differentBytes = "classB".toByteArray()

        val classHash = manager.computeHash(differentBytes)
        val testHash = manager.computeHash(testBytes)

        val mutationId = "ARITHMETIC_com.Foo_test1_10"

        val oldHistory =
            MutationHistoryManager.MutationHistory(
                classHashes = mapOf("com.Foo" to classHash),
                testHashes = mapOf("com.Foo" to testHash),
                results =
                    mapOf(
                        mutationId to
                            MutationHistoryManager.MutationStatusResult(
                                status = MutationStatus.KILLED,
                            ),
                    ),
            )

        val reusable = manager.getReusableResults(classBytes, testBytes, oldHistory)
        assertEquals(0, reusable.size)
    }

    @Test
    fun `updateHistory preserves existing entries and adds new ones`() {
        val manager = MutationHistoryManager(File(tempDir.toFile(), "dummy"))
        val classBytes = "classA".toByteArray()
        val testBytes = "testA".toByteArray()

        val oldHistory =
            MutationHistoryManager.MutationHistory(
                classHashes = mapOf("com.Foo" to "oldHash"),
                testHashes = mapOf("com.FooTest" to "oldTestHash"),
                results =
                    mapOf(
                        "com.Foo_com.FooTest_m1" to
                            MutationHistoryManager.MutationStatusResult(
                                status = MutationStatus.KILLED,
                            ),
                    ),
            )

        val newResults = mapOf("com.Foo_com.FooTest_m2" to MutationStatus.SURVIVED)
        val updated =
            manager.updateHistory(
                oldHistory,
                "com.Foo",
                "com.FooTest",
                classBytes,
                testBytes,
                newResults,
            )

        assertEquals(2, updated.results.size)
        assertEquals(MutationStatus.KILLED, updated.results["com.Foo_com.FooTest_m1"]?.status)
        assertEquals(MutationStatus.SURVIVED, updated.results["com.Foo_com.FooTest_m2"]?.status)
        assertNotNull(updated.classHashes["com.Foo"])
        assertNotNull(updated.testHashes["com.FooTest"])
    }
}
