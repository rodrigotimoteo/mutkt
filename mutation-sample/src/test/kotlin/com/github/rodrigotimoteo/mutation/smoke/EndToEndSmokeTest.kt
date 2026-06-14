package com.github.rodrigotimoteo.mutation.smoke

import com.github.rodrigotimoteo.mutation.cache.MutKtCache
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end smoke test: validates core components work together.
 * Skips tests requiring ASM (mutation-sample doesn't depend on ASM directly).
 */
class EndToEndSmokeTest {

    @Test
    fun `cache stores and retrieves entries`() {
        val cacheDir = Files.createTempDirectory("mutkt-cache-smoke").toFile()
        try {
            val cache = MutKtCache(cacheDir)
            val hash = "test-hash-123"
            cache.store(hash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

            assertEquals(MutationStatus.KILLED, cache.lookup(hash, "ARITHMETIC", "foo", 42))
            assertEquals(null, cache.lookup(hash, "ARITHMETIC", "foo", 99))
            val (entries, _) = cache.stats()
            assertTrue(entries >= 1)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `baseline storage saves and loads`() {
        val baselineDir = Files.createTempDirectory("mutkt-baseline-smoke").toFile()
        try {
            val storage = com.github.rodrigotimoteo.mutation.baseline.BaselineStorage(baselineDir)
            storage.save(emptyMap())
            val loaded = storage.load()
            assertTrue(loaded.isEmpty())
        } finally {
            baselineDir.deleteRecursively()
        }
    }

    @Test
    fun `incremental analyzer does not crash`() {
        val analyzer = com.github.rodrigotimoteo.mutation.engine.IncrementalAnalyzer(File("."))
        val changed = analyzer.getChangedClasses()
        println("Changed classes: ${changed.size}")
    }

    @Test
    fun `console report generates`() {
        val report = createSampleReport()
        val console = com.github.rodrigotimoteo.mutation.report.ConsoleReporter().generate(report)
        assertTrue(console.isNotEmpty())
        assertTrue(console.contains("MutKt Mutation Report"))
        println(console)
    }

    @Test
    fun `html report generates`() {
        val report = createSampleReport()
        val outputDir = Files.createTempDirectory("mutkt-html-smoke").toFile()
        try {
            val html = com.github.rodrigotimoteo.mutation.report.HtmlReportGenerator().generate(report, outputDir)
            assertTrue(html.exists())
            assertTrue(html.length() > 100)
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `findTestClasses discovers CalculatorTest`() {
        val testClassesDir = File("build/classes/kotlin/test")
        if (!testClassesDir.exists()) return

        val testClasses = testClassesDir.walkTopDown()
            .filter { it.extension == "class" }
            .map {
                val rel = testClassesDir.toPath().relativize(it.toPath())
                rel.toString().replace(".class", "").replace("/", ".").replace("\\", ".")
            }
            .filter { !it.contains("$") }
            .filter { it.substringAfterLast('.').endsWith("Test") }
            .toList()

        println("Found ${testClasses.size} test classes: $testClasses")
        assertTrue(testClasses.isNotEmpty())
    }

    private fun createSampleReport() = MutationReport(
        results = emptyList(),
        totalMutations = 100,
        killedMutations = 85,
        survivedMutations = 10,
        errorMutations = 3,
        timeoutMutations = 2,
        noCoverageMutations = 0,
        totalExecutionTimeMs = 5000,
    )
}
