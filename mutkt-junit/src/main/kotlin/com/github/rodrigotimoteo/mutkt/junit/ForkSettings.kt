package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Path
import java.nio.file.Paths

/**
 * How [ForkedTestExecutor] launches its child JVMs.
 * [environment] is added to the parent's; [jvmArgs] carry heap, `-D` properties and the like;
 * [classpath] is the project's test runtime classpath without class directories.
 * [engineClasspath] is the child's own classpath (null means [derivedEngineClasspath]); its Platform jars are
 * replaced by the project's own when [classpath] has the launcher.
 * [engines] are JUnit Platform engine ids (`junit-jupiter`, `junit-vintage`); [includeTags] and [excludeTags] are
 * JUnit tag expressions, applied to every run (no include tags means all tests).
 * A child gets [prepareMillis] to load and link classes and discover all test classes (their plans are held
 * until the run ends); silence for longer fails the run
 * (not a timeout, so it earns no detection credit). Once it reports the start, a reply is awaited for the
 * mutant's deadline plus [graceMillis].
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
    val prepareMillis: Long = DEFAULT_PREPARE_MILLIS,
    val engineClasspath: List<Path>? = null,
    val engines: List<String> = listOf(JUPITER_ENGINE),
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
) {
    init {
        require(parallelism >= 1) { "parallelism must be at least 1: $parallelism" }
        require(engines.isNotEmpty()) { "The 'engines' setting must not be empty" }
        require(includeTags.none(::hasLineBreak)) { "includeTags must not contain line breaks: $includeTags" }
        require(excludeTags.none(::hasLineBreak)) { "excludeTags must not contain line breaks: $excludeTags" }
        require(graceMillis >= 0) { "graceMillis must not be negative: $graceMillis" }
        require(prepareMillis > 0) { "prepareMillis must be positive: $prepareMillis" }
    }

    private companion object {
        const val DEFAULT_GRACE_MILLIS: Long = 5000
        const val DEFAULT_PREPARE_MILLIS: Long = 60_000
    }
}

// The startup file holds one entry per line.
private fun hasLineBreak(value: String) = '\n' in value || '\r' in value

// The child needs the fork entry point, the domain types, the Launcher API with its engine, commons and
// opentest4j (the fallback when the project has no launcher, see engineClasspathFor; shared with the project's
// engines, see LauncherApiLoader) and the Kotlin stdlib.
// Located through code sources so it works from jars (plugin) and from class directories (tests).
private val engineClassNames =
    listOf(ForkedTestExecutor::class.java.name, "com.github.rodrigotimoteo.mutkt.domain.Mutant", "kotlin.Unit") +
        platformClassNames

/** The jars or class directories a forked child needs, taken from where this plugin itself was loaded. */
public fun derivedEngineClasspath(): List<Path> =
    engineClassNames
        .map { name ->
            val source = Class.forName(name).protectionDomain.codeSource
            val location = checkNotNull(source?.location) { "No code source for $name" }
            Paths.get(location.toURI())
        }.distinct()
