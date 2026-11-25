package com.github.rodrigotimoteo.mutkt.junit

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

// Launcher, engine API, commons and opentest4j: the jars that must be one consistent version in the child.
// The launcher comes first; it decides whether the project brings its own platform.
internal val platformClassNames =
    listOf(
        "org.junit.platform.launcher.core.LauncherFactory",
        "org.junit.platform.engine.TestEngine",
        "org.junit.platform.commons.JUnitException",
        "org.opentest4j.AssertionFailedError",
    )

private val platformMarkers = platformClassNames.map { it.replace('.', '/') + ".class" }
private val LAUNCHER = platformMarkers.first()

// A project's engines are built against its own Platform, so its jars win when it has the launcher (Gradle 8 projects
// often have none: the launcher is injected at run time, hence the bundled fallback). Matching by content, not by
// file name, because jars may be renamed or repackaged.
internal fun engineClasspathFor(
    engine: List<Path>,
    project: List<Path>,
): List<Path> {
    val own = platformMarkers.associateWith { marker -> project.firstOrNull { it.holds(marker) } }
    if (own.getValue(LAUNCHER) == null) return engine
    val platform =
        platformMarkers
            .map { marker -> own[marker] ?: checkNotNull(engine.firstOrNull { it.holds(marker) }) { "No $marker" } }
            .distinct()
    return engine.filterNot { entry -> platformMarkers.any(entry::holds) } + platform
}

private fun Path.holds(resource: String): Boolean =
    when {
        Files.isDirectory(this) -> Files.exists(resolve(resource))
        fileName.toString().endsWith(".jar") && Files.isRegularFile(this) -> jarHolds(resource)
        else -> false
    }

private fun Path.jarHolds(resource: String): Boolean =
    try {
        ZipFile(toFile()).use { it.getEntry(resource) != null }
    } catch (e: IOException) {
        throw IllegalStateException("Cannot read the classpath entry $this: ${e.message}", e)
    }
