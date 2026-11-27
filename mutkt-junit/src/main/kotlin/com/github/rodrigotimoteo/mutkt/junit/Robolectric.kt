package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val ROBOLECTRIC_MARKER = "org/robolectric/internal/SandboxManager.class"

/**
 * True when [entry] holds Robolectric. Then no child is reused: Robolectric caches its sandbox and the instrumented
 * project classes in statics with no reset API, and that a fresh class loader per run resets all of them is not shown
 * on a real Robolectric project. Only jars and directories can hold classes; other entries are skipped.
 */
internal fun hasRobolectric(entry: Path): Boolean =
    when {
        Files.isDirectory(entry) -> Files.isRegularFile(entry.resolve(ROBOLECTRIC_MARKER))
        Files.isRegularFile(entry) && entry.toString().endsWith(".jar") ->
            ZipFile(entry.toFile()).use { it.getEntry(ROBOLECTRIC_MARKER) != null }
        else -> false
    }
