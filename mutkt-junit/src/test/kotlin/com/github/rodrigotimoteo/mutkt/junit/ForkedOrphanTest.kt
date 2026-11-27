package com.github.rodrigotimoteo.mutkt.junit

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

// A cancelled build can end the daemon (shutdown hooks run) or kill it outright (none run); no child may outlive it.
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedOrphanTest {
    @TempDir
    lateinit var dir: Path

    private val processes = mutableListOf<ProcessHandle>()

    @AfterEach
    fun killAll() {
        processes.forEach { it.destroyForcibly() }
    }

    @Test
    fun `a child exits when its parent is killed`() {
        val (parent, child) = parentWithSleepingChild()
        parent.destroyForcibly()

        assertGoneSoon(child)
    }

    @Test
    fun `a parent that shuts down kills its children and the processes their tests forked`() {
        val (parent, child, grandchild) = parentWithSleepingChild()
        parent.destroy()

        assertGoneSoon(child)
        assertGoneSoon(grandchild)
    }

    private fun parentWithSleepingChild(): Triple<Process, ProcessHandle, ProcessHandle> {
        val marker = dir.resolve("grandchild.pid")
        val settings = forkSettingsFor(dir, sleepingTest(marker))
        val parent =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                "com.github.rodrigotimoteo.mutkt.junit.ParentHarness",
                settings.projectClassDir.toString(),
                settings.testClassDir.toString(),
            ).inheritIO()
                .start()
        processes.add(parent.toHandle())
        val grandchild = awaitPid(marker, parent::isAlive).also(processes::add)
        val child = grandchild.parent().orElseThrow().also(processes::add)
        return Triple(parent, child, grandchild)
    }
}
