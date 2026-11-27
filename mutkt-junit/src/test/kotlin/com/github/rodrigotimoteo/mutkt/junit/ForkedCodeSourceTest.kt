package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

// Tools map a class's code source back to its sources (strikt's filepeek), so it must be Gradle's output directory,
// not MutKt's staged copy.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedCodeSourceTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private val test =
        """
        class CalcTest {
            @Test fun codeSources() {
                assertEquals(System.getProperty("testOrigin"), locationOf(CalcTest::class.java))
                assertEquals(System.getProperty("mainOrigin"), locationOf(Calc::class.java))
            }

            private fun locationOf(type: Class<*>) =
                java.nio.file.Path.of(type.protectionDomain.codeSource.location.toURI()).toString()
        }
        """.trimIndent()

    @Test
    fun `classes report their original output directory`() {
        val main = Files.createDirectories(dir.resolve("build/classes/kotlin/main"))
        val tests = Files.createDirectories(dir.resolve("build/classes/kotlin/test"))
        val settings =
            forkSettingsFor(dir, test).copy(
                jvmArgs = listOf("-DmainOrigin=$main", "-DtestOrigin=$tests"),
                codeSources = mapOf("Calc" to main, "CalcTest" to tests),
            )
        val pool = ForkedTestExecutor(settings).also { executor = it }
        assertEquals(Outcome.Survived, pool.baseline(deadline).outcome)
    }
}
