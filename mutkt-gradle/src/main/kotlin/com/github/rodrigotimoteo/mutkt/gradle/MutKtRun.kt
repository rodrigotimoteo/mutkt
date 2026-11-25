package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.AnalysisConfig
import com.github.rodrigotimoteo.mutkt.application.ClassFilters
import com.github.rodrigotimoteo.mutkt.application.Glob
import com.github.rodrigotimoteo.mutkt.application.RunMutationAnalysis
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.junit.ForkSettings
import com.github.rodrigotimoteo.mutkt.junit.ForkedTestExecutor
import com.github.rodrigotimoteo.mutkt.report.ConsoleReportWriter
import com.github.rodrigotimoteo.mutkt.report.FileSourceLookup
import org.gradle.api.GradleException
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

internal fun MutKtTask.runAnalysis(): AnalysisResult {
    checkThresholdRange(failBelowScore.orNull)
    val production = stage(classesDirs.files, File(temporaryDir, "classes"))
    val tests = stage(testClassesDirs.files, File(temporaryDir, "test-classes"))
    val classDirs = classesDirs.files + testClassesDirs.files
    // Class directories reach the child through the staged copies, so only jars and resource directories remain.
    val dependencies = testRuntimeClasspath.files.filterNot { it in classDirs }.map(File::toPath)
    val config = analysisConfig()
    val summary = StringBuilder()
    val writer = ConsoleReportWriter(summary, FileSourceLookup(sourceDirs.files.map(File::toPath)))
    // The generator resolves supertypes of the analysed classes, including the ones the filters left out.
    val urls = (listOf(production) + dependencies).map { it.toUri().toURL() }.toTypedArray()
    val report =
        URLClassLoader(urls, null).use { loader ->
            // Built first so an unknown operator id fails before any child JVM could start.
            val generator = generator(loader)
            ForkedTestExecutor(forkSettings(production, tests, dependencies, config.parallelism)).use { executor ->
                RunMutationAnalysis(DirectoryClassSource(production), generator, executor, writer, config).run()
            }
        }
    return AnalysisResult(
        summary.toString(),
        report.score,
        report.results.size,
        report.results.count { it.status == MutantStatus.ERROR },
    )
}

private fun MutKtTask.generator(loader: ClassLoader) =
    try {
        AsmMutantGenerator(operators.get()) { name -> loader.classBytes(name) }
    } catch (e: IllegalArgumentException) {
        throw GradleException(e.message.orEmpty(), e)
    }

private fun ClassLoader.classBytes(name: String): ByteArray? =
    getResourceAsStream("$name.class")?.use { it.readBytes() }

private fun MutKtTask.analysisConfig() =
    AnalysisConfig(
        ClassFilters(
            includes = targetClasses.get().map(::Glob),
            excludes = excludedClasses.get().map(::Glob),
            excludedMethods = excludedMethods.get().toSet(),
            maxMutantsPerClass = maxMutantsPerClass.orNull,
        ),
        timeoutMs.get().milliseconds,
        threads.orNull ?: Runtime.getRuntime().availableProcessors(),
    )

internal fun MutKtTask.forkSettings(
    production: Path,
    tests: Path,
    dependencies: List<Path>,
    parallelism: Int,
) = ForkSettings(
    javaExecutable = Path.of(testJavaExecutable.get()),
    jvmArgs = testJvmArgs.get(),
    // The child inherits the daemon's environment; the test task's own entries are deliberately not forwarded.
    environment = emptyMap(),
    workingDir = Path.of(testWorkingDir.get()),
    projectClassDir = production,
    testClassDir = tests,
    classpath = dependencies,
    parallelism = parallelism,
    engineClasspath = engineClasspath(),
    engines = engines.get().toList(),
    includeTags = includeTags.get().toList(),
    excludeTags = excludeTags.get().toList(),
)

// Several class directories (Kotlin and Java output) become one, because the fork reads a single root per side.
private fun stage(
    dirs: Set<File>,
    into: File,
): Path {
    into.deleteRecursively()
    into.mkdirs()
    dirs.filter(File::isDirectory).forEach { it.copyRecursively(into, overwrite = true) }
    return into.toPath()
}

// The child needs the fork entry point, the domain types, the Launcher API with its engine, commons, opentest4j
// and the Kotlin stdlib. Each marker class is located through its code source, so the list holds the plugin's own
// jars (or class directories under test) instead of whatever Gradle bundles on the daemon classpath.
private val engineMarkers =
    listOf(
        ForkedTestExecutor::class.java.name,
        "com.github.rodrigotimoteo.mutkt.domain.Mutant",
        "kotlin.Unit",
        "org.junit.platform.launcher.core.LauncherFactory",
        "org.junit.platform.engine.TestEngine",
        "org.junit.platform.commons.JUnitException",
        "org.opentest4j.AssertionFailedError",
    )

private fun engineClasspath(): List<Path> =
    engineMarkers
        .map { name ->
            val location =
                Class
                    .forName(name)
                    .protectionDomain.codeSource
                    ?.location
            Path.of(checkNotNull(location) { "No code source for $name" }.toURI())
        }.distinct()
