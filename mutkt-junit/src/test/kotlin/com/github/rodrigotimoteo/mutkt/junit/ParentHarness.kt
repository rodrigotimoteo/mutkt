@file:JvmName("ParentHarness")

package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/** Stands in for the Gradle daemon: runs the baseline of the class directories `args[0]` and `args[1]` in a child. */
internal fun main(args: Array<String>) {
    val (projectClassDir, testClassDir) = args
    val settings =
        ForkSettings(
            Path.of(System.getProperty("java.home"), "bin", "java"),
            emptyList(),
            emptyMap(),
            null,
            Path.of(projectClassDir),
            Path.of(testClassDir),
            runtimeJars(),
            1,
        )
    ForkedTestExecutor(settings).use { it.baseline(10.minutes) }
}
