package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

// Libraries extract natives and temp files per JVM; a killed child never runs their cleanup.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedTempDirTest {
    @TempDir
    lateinit var dir: Path

    private val prefix = "mutkt-probe-${UUID.randomUUID()}"
    private val systemTmp = Path.of(System.getProperty("java.io.tmpdir"))
    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private fun executor(jvmArgs: List<String> = emptyList()): ForkedTestExecutor {
        val settings = forkSettingsFor(dir, PROBE_TEST)
        val probeArgs = listOf("-DprobePrefix=$prefix", "-DprobeOut=${dir.resolve("seen")}")
        return ForkedTestExecutor(settings.copy(jvmArgs = jvmArgs + probeArgs)).also { executor = it }
    }

    // The java.io.tmpdir the child test saw.
    private fun childTmp(): Path = Path.of(dir.resolve("seen").readText())

    private fun leftInSystemTmp() = systemTmp.listDirectoryEntries("$prefix*")

    @Test
    fun `a child writes temp files into its own directory, deleted once the child ends`() {
        val pool = executor()
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        val tmp = childTmp()
        assertNotEquals(systemTmp.toRealPath(), tmp.toRealPath())
        assertEquals(2, tmp.listDirectoryEntries("$prefix*").size)

        pool.close()
        assertFalse(Files.exists(tmp))
        assertTrue(Files.notExists(tmp.parent) || tmp.parent.listDirectoryEntries().isEmpty())
        assertEquals(emptyList<Path>(), leftInSystemTmp())
    }

    @Test
    fun `a killed child leaves nothing behind, before the executor closes`() {
        val pool = executor()
        assertEquals(Outcome.Timeout, pool.run(mutantOf(), SPIN, 1.seconds))
        assertFalse(Files.exists(childTmp()))
        assertEquals(emptyList<Path>(), leftInSystemTmp())
    }

    @Test
    fun `a java io tmpdir set by the user is used and left alone`() {
        val own = Files.createDirectory(dir.resolve("own"))
        val pool = executor(listOf("-Djava.io.tmpdir=$own"))
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        pool.close()
        assertEquals(own.toRealPath(), childTmp().toRealPath())
        assertEquals(2, own.listDirectoryEntries("$prefix*").size)
    }

    private companion object {
        val PROBE_TEST =
            """
            class CalcTest {
                @Test fun adds() {
                    val prefix = System.getProperty("probePrefix")
                    java.io.File(System.getProperty("probeOut")).writeText(System.getProperty("java.io.tmpdir"))
                    java.io.File.createTempFile(prefix, ".tmp")
                    java.nio.file.Files.createTempDirectory(prefix).resolve("inner").toFile().writeText("x")
                    assertEquals(5, Calc().add(2, 3))
                }
            }
            """.trimIndent()

        // Only a kill stops it, after the test wrote its temp files.
        val SPIN =
            mutantBytes("class Calc { fun add(a: Int, b: Int): Int { while (System.nanoTime() > 0) {}; return 0 } }")
    }
}
