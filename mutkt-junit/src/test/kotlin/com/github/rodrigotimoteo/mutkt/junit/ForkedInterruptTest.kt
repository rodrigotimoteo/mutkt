package com.github.rodrigotimoteo.mutkt.junit

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

// Gradle cancels a task by interrupting its thread; the child JVM it was using must not outlive that.
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedInterruptTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null
    private val processes = mutableListOf<ProcessHandle>()

    @AfterEach
    fun cleanUp() {
        processes.forEach { it.destroyForcibly() }
        executor?.close()
    }

    @Test
    fun `interrupting a run kills its child and the processes its tests forked`() {
        val marker = dir.resolve("grandchild.pid")
        val pool = ForkedTestExecutor(forkSettingsFor(dir, sleepingTest(marker))).also { executor = it }
        val runner = runInThread(pool)
        val grandchild = awaitPid(marker, runner::isAlive).also(processes::add)
        val child = grandchild.parent().orElseThrow().also(processes::add)

        runner.interrupt()

        assertGoneSoon(child)
        assertGoneSoon(grandchild)
    }

    @Test
    fun `interrupting a child's start kills it`() {
        val pidFile = dir.resolve("child.pid")
        // Never answers Ready, so the run waits in the handshake.
        val java = dir.resolve("java")
        Files.writeString(java, "#!/bin/sh\necho $$ > $pidFile.tmp && mv $pidFile.tmp $pidFile\nexec sleep 300\n")
        java.toFile().setExecutable(true)
        val settings = forkSettingsFor(dir, "class CalcTest").copy(javaExecutable = java)
        val runner = runInThread(ForkedTestExecutor(settings).also { executor = it })
        val child = awaitPid(pidFile, runner::isAlive).also(processes::add)

        runner.interrupt()

        assertGoneSoon(child)
    }

    private fun runInThread(pool: ForkedTestExecutor): Thread =
        thread(name = "mutkt-run") { runCatching { pool.run(mutantOf(), mutantBytes(CALC), 10.minutes) } }
}
