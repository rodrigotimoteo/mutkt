package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedVintageEngineTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    @Test
    fun `a JUnit 4 project without a platform engine runs on the supplied vintage engine`() {
        // JUnit 4 and the Jupiter API stay; no launcher and no vintage engine, as in a useJUnit() build.
        val classpath =
            runtimeJars().filterNot {
                val name = it.fileName.toString()
                name.startsWith("junit-platform-launcher") || name.startsWith("junit-vintage-engine")
            }
        val settings = forkSettingsFor(dir, JUNIT4_TEST, classpath).copy(engines = listOf("junit-vintage"))
        val pool = ForkedTestExecutor(settings).also { executor = it }

        assertTrue(pool.run(mutantOf(), mutantBytes(), 60.seconds) is Outcome.Killed)
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
    }

    private companion object {
        const val JUNIT4_TEST =
            "class CalcTest { @org.junit.Test fun adds() { org.junit.Assert.assertEquals(5, Calc().add(2, 3)) } }"
    }
}
