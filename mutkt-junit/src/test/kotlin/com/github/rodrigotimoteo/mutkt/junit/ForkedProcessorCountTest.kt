package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

// A Robolectric child keeps several cores busy, so it sees at most two.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedProcessorCountTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    // The processor count the child's test saw.
    private fun seen(
        robolectric: Boolean,
        jvmArgs: List<String> = emptyList(),
    ): Int {
        val settings = forkSettingsFor(dir, PROBE_TEST)
        val extra = if (robolectric) listOf(writeJar(dir.resolve("robolectric.jar"), sandboxManager)) else emptyList()
        val args = jvmArgs + "-DprobeOut=${dir.resolve("seen")}"
        val pool =
            ForkedTestExecutor(settings.copy(jvmArgs = args, classpath = settings.classpath + extra))
                .also { executor = it }
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        return dir.resolve("seen").readText().toInt()
    }

    private val host = Runtime.getRuntime().availableProcessors()

    @Test
    fun `a Robolectric child sees at most two processors`() {
        assertEquals(minOf(2, host), seen(robolectric = true))
    }

    @Test
    fun `a processor count set by the build wins`() {
        assertEquals(1, seen(robolectric = true, listOf("-XX:ActiveProcessorCount=1")))
    }

    @Test
    fun `without Robolectric a child sees every processor`() {
        assertEquals(host, seen(robolectric = false))
    }

    @Test
    fun `a single-processor host is not raised`() {
        assertEquals(listOf("-XX:ActiveProcessorCount=1"), robolectricJvmArgs(processors = 1))
    }

    private companion object {
        val sandboxManager = compileKotlin("package org.robolectric.internal\n\nclass SandboxManager")

        val PROBE_TEST =
            """
            class CalcTest {
                @Test fun adds() {
                    val processors = Runtime.getRuntime().availableProcessors().toString()
                    java.io.File(System.getProperty("probeOut")).writeText(processors)
                    assertEquals(5, Calc().add(2, 3))
                }
            }
            """.trimIndent()
    }
}
