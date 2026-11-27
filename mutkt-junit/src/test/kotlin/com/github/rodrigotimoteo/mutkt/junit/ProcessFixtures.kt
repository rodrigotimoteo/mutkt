package com.github.rodrigotimoteo.mutkt.junit

import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val POLL_MILLIS = 100L
private const val GONE_SECONDS = 5L
private const val APPEAR_SECONDS = 30L

/** A test that forks `sleep`, writes that grandchild's pid to [marker], then sleeps forever. */
internal fun sleepingTest(marker: Path): String =
    "class CalcTest { @Test fun adds() { val p = ProcessBuilder(\"sleep\", \"300\").start(); " +
        "java.io.File(\"$marker\").writeText(p.pid().toString()); Thread.sleep(Long.MAX_VALUE) } }"

/** The process whose pid [file] comes to hold while [alive] stays true. */
internal fun awaitPid(
    file: Path,
    alive: () -> Boolean,
): ProcessHandle {
    val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(APPEAR_SECONDS)
    while (true) {
        val pid = if (Files.exists(file)) Files.readString(file).trim().toLongOrNull() else null
        if (pid != null) return ProcessHandle.of(pid).orElseThrow { AssertionError("pid $pid already gone") }
        check(alive() && System.nanoTime() < limit) { "no pid in $file" }
        Thread.sleep(POLL_MILLIS)
    }
}

internal fun assertGoneSoon(process: ProcessHandle) {
    val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(GONE_SECONDS)
    while (process.isAlive && System.nanoTime() < limit) Thread.sleep(POLL_MILLIS)
    assertFalse(process.isAlive, "process ${process.pid()} still alive after ${GONE_SECONDS}s")
}
