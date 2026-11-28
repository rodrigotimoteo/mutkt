package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File

private const val PLATFORM = "org.junit.platform"

/**
 * `junit-platform-launcher` at the version of `junit-platform-engine` when [configuration] has the engine API and no
 * launcher, so the child JVMs run the project's Platform rather than the bundled one. Gradle 9 adds no launcher to a
 * build and Gradle 8 adds it to the `test` task only. Resolved lazily, only when `mutkt` runs; one that cannot be
 * resolved leaves the bundled Platform, with a warning.
 */
internal fun Project.missingPlatformLauncher(
    configuration: Provider<Configuration>,
    engines: Provider<Set<String>>,
): FileCollection {
    // Once per build: without the configuration cache Gradle queries the collection several times.
    val launcher =
        lazy {
            // The dependency graph, not the jars: probing files at configuration time would tie the configuration
            // cache to build outputs on the classpath.
            val modules =
                configuration
                    .get()
                    .incoming.resolutionResult.allComponents
                    .mapNotNull { it.moduleVersion }
            launcherVersion(modules, engines.get())?.let(::resolveLauncher).orEmpty()
        }
    return files(providers.provider { launcher.value })
}

private fun launcherVersion(
    modules: List<ModuleVersionIdentifier>,
    engines: Set<String>,
): String? {
    fun has(
        group: String,
        name: String,
    ) = modules.firstOrNull { it.group == group && it.name == name }
    // MutKt supplies a missing vintage engine of its own version, which runs only on the bundled Platform.
    val suppliedVintage = "junit-vintage" in engines && has("org.junit.vintage", "junit-vintage-engine") == null
    if (suppliedVintage || has(PLATFORM, "junit-platform-launcher") != null) return null
    return has(PLATFORM, "junit-platform-engine")?.version
}

private fun Project.resolveLauncher(version: String): Set<File> {
    val notation = "$PLATFORM:junit-platform-launcher:$version"
    // Deliberately not a copy of the test runtime classpath's resolution strategy: one pinned, non-transitive jar.
    val launcher = configurations.detachedConfiguration(dependencies.create(notation)).apply { isTransitive = false }
    // Unresolvable (offline, no repository has it) keeps the bundled Platform, which ran these builds before.
    val files =
        launcher.incoming
            .artifactView { it.lenient(true) }
            .files.files
    if (files.isEmpty()) {
        logger.warn(
            "MutKt cannot resolve $notation, so the tests run on the JUnit Platform bundled with MutKt, which may " +
                "not match your engine: add testRuntimeOnly(\"$notation\") to the dependencies",
        )
    }
    return files
}
