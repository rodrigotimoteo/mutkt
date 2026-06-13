package com.github.rodrigotimoteo.mutation.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TestStrengthOrderingFullTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var ordering: TestStrengthOrdering

    @BeforeEach
    fun setUp() {
        ordering = TestStrengthOrdering(tempDir)
    }

    @Test
    fun `flushHistory persists multiple entries and loadHistory restores them`() {
        ordering.recordResults("TestA", 7, 20)
        ordering.recordResults("TestB", 3, 10)
        ordering.flushHistory()

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()

        assertEquals(2, stats.size)
        assertEquals(7, stats["TestA"]?.totalKills)
        assertEquals(3, stats["TestB"]?.totalKills)
        assertEquals(20, stats["TestA"]?.totalMutations)
        assertEquals(10, stats["TestB"]?.totalMutations)
    }

    @Test
    fun `flushHistory persists single entry without trailing comma`() {
        ordering.recordResults("SoloTest", 4, 8)
        ordering.flushHistory()

        val content = File(tempDir, ".mutkt/test-strength.json").readText()
        assertTrue(content.contains("\"SoloTest\":"))
        assertTrue(content.contains("\"totalKills\": 4"))

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()
        assertEquals(1, stats.size)
        assertEquals(4, stats["SoloTest"]?.totalKills)
    }

    @Test
    fun `flushHistory with empty cache writes empty JSON object`() {
        ordering.flushHistory()

        val content = File(tempDir, ".mutkt/test-strength.json").readText()
        assertTrue(content.contains("{"))
        assertTrue(content.contains("}"))

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `loadHistory ignores blank lines and unknown properties`() {
        File(tempDir, ".mutkt").mkdirs()
        val file = File(tempDir, ".mutkt/test-strength.json")
        file.writeText(
            """
            {
              "TestA": {
                "totalKills": 5,
                "unknownProp": 999,
            """.trimIndent() +
                "\n" +
                """
                    "totalRuns": 2,
                    "totalMutations": 10,
                    "lastRun": 1234
                  }

                }
                """.trimIndent(),
        )

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()

        assertEquals(1, stats.size)
        assertEquals(5, stats["TestA"]?.totalKills)
        assertEquals(2, stats["TestA"]?.totalRuns)
        assertEquals(10, stats["TestA"]?.totalMutations)
        assertEquals(1234L, stats["TestA"]?.lastRun)
    }

    @Test
    fun `loadHistory skips entries with invalid numeric fields`() {
        File(tempDir, ".mutkt").mkdirs()
        val file = File(tempDir, ".mutkt/test-strength.json")
        file.writeText(
            """
            {
              "Good": {
                "totalKills": 5,
                "totalRuns": 2,
                "totalMutations": 10,
                "lastRun": 1234
              },
              "Bad": {
                "totalKills": notanumber,
                "totalRuns": 2,
                "totalMutations": 10,
                "lastRun": 1234
              }
            }
            """.trimIndent(),
        )

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()

        assertEquals(2, stats.size)
        assertEquals(0, stats["Bad"]?.totalKills)
        assertEquals(2, stats["Bad"]?.totalRuns)
    }

    @Test
    fun `loadHistory handles fields in reverse order`() {
        File(tempDir, ".mutkt").mkdirs()
        val file = File(tempDir, ".mutkt/test-strength.json")
        file.writeText(
            """
            {
              "TestZ": {
                "lastRun": 9999,
                "totalMutations": 50,
                "totalRuns": 7,
                "totalKills": 12
              }
            }
            """.trimIndent(),
        )

        val fresh = TestStrengthOrdering(tempDir)
        val stats = fresh.getStats()

        assertEquals(1, stats.size)
        assertEquals(12, stats["TestZ"]?.totalKills)
        assertEquals(7, stats["TestZ"]?.totalRuns)
        assertEquals(50, stats["TestZ"]?.totalMutations)
        assertEquals(9999L, stats["TestZ"]?.lastRun)
    }

    @Test
    fun `getStrengthScore returns 0 when totalMutations is 0`() {
        ordering.recordResults("ZeroTest", 0, 0)

        val score = ordering.getStrengthScore("ZeroTest")

        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `orderTests places unknown tests last`() {
        ordering.recordResults("Known", 5, 10)

        val tests = listOf("UnknownA", "Known", "UnknownB")
        val ordered = ordering.orderTests(tests)

        assertEquals("Known", ordered[0])
        assertTrue(ordered.drop(1).containsAll(listOf("UnknownA", "UnknownB")))
        assertEquals(3, ordered.size)
    }
}
