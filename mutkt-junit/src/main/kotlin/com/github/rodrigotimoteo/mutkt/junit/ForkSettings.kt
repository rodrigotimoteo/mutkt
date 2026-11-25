package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Path
import java.nio.file.Paths

/**
 * How [ForkedTestExecutor] launches its child JVMs.
 * [environment] is added to the parent's; [jvmArgs] carry heap, `-D` properties and the like;
 * [classpath] is the project's test runtime classpath without class directories.
 * [engineClasspath] is the child's own classpath (null derives it from code sources, which Gradle wiring must not
 * rely on: they can resolve to Gradle's bundled jars, so pass it explicitly).
 * A reply is awaited for the mutant's deadline plus [graceMillis], which also covers JVM-side class loading.
 */
public data class ForkSettings(
    val javaExecutable: Path,
    val jvmArgs: List<String>,
    val environment: Map<String, String>,
    val workingDir: Path?,
    val projectClassDir: Path,
    val testClassDir: Path,
    val classpath: List<Path>,
    val parallelism: Int,
    val graceMillis: Long = DEFAULT_GRACE_MILLIS,
    val engineClasspath: List<Path>? = null,
) {
    init {
        require(parallelism >= 1) { "parallelism must be at least 1: $parallelism" }
        require(graceMillis >= 0) { "graceMillis must not be negative: $graceMillis" }
    }

    private companion object {
        const val DEFAULT_GRACE_MILLIS: Long = 5000
    }
}

// The child needs the fork entry point, the domain types, the Launcher API with its engine, commons and
// opentest4j (shared with the project's engines, see LauncherApiLoader) and the Kotlin stdlib. Located through
// code sources so it works from jars (plugin) and from class directories (tests).
private val engineClassNames =
    listOf(
        ForkedTestExecutor::class.java.name,
        "com.github.rodrigotimoteo.mutkt.domain.Mutant",
        "kotlin.Unit",
        "org.junit.platform.launcher.core.LauncherFactory",
        "org.junit.platform.engine.TestEngine",
        "org.junit.platform.commons.JUnitException",
        "org.opentest4j.AssertionFailedError",
    )

internal fun derivedEngineClasspath(): List<Path> =
    engineClassNames
        .map { name ->
            val source = Class.forName(name).protectionDomain.codeSource
            val location = checkNotNull(source?.location) { "No code source for $name" }
            Paths.get(location.toURI())
        }.distinct()
