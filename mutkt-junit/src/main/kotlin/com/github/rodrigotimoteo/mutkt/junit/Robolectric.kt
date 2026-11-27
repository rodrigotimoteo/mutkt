package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val MAX_PROCESSORS = 2
private const val ROBOLECTRIC_MARKER = "org/robolectric/internal/SandboxManager.class"

/**
 * True when [entry] holds Robolectric. Then no child is reused: a fresh class loader per run does not reset everything
 * Robolectric sets up, since it installs Conscrypt as a JVM-wide security provider, and on a reused child every later
 * run that needs a SecureRandom (a temp file, TLS) then fails. Only jars and directories can hold classes.
 */
internal fun hasRobolectric(entry: Path): Boolean =
    when {
        Files.isDirectory(entry) -> Files.isRegularFile(entry.resolve(ROBOLECTRIC_MARKER))
        Files.isRegularFile(entry) && entry.toString().endsWith(".jar") ->
            ZipFile(entry.toFile()).use { it.getEntry(ROBOLECTRIC_MARKER) != null }
        else -> false
    }

// A Robolectric child keeps several cores busy (its JIT, GC and executors are sized by the core count), so a full pool
// of children that each see every core overloads the machine; two cores each ran the same runs faster. They go before
// the build's JVM arguments, so a build that sets the flag itself wins.
internal fun robolectricJvmArgs(processors: Int = Runtime.getRuntime().availableProcessors()): List<String> =
    listOf("-XX:ActiveProcessorCount=${minOf(MAX_PROCESSORS, processors)}")
