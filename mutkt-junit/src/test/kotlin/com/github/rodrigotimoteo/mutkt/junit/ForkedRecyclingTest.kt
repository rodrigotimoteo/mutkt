package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

// A run whose class loader stays reachable (here a thread holding most of the heap) must not slow or kill later runs.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedRecyclingTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private fun childrenFor(test: String): Int {
        val settings = forkSettingsFor(dir, test).copy(jvmArgs = listOf("-Xmx256m"))
        val pool = ForkedTestExecutor(settings).also { executor = it }
        assertEquals(Outcome.Survived, pool.baseline(60.seconds).outcome)
        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        return pool.startedChildren
    }

    @Test
    fun `a child left holding most of its heap is replaced`() {
        val test =
            "class CalcTest { @Test fun adds() { val hog = ByteArray(220 shl 20); " +
                "Thread { Thread.sleep(Long.MAX_VALUE); hog.size }.apply { isDaemon = true }.start(); " +
                "assertEquals(5, Calc().add(2, 3)) } }"
        assertEquals(2, childrenFor(test))
    }

    @Test
    fun `a child within its limits is reused`() {
        assertEquals(1, childrenFor("class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"))
    }
}
