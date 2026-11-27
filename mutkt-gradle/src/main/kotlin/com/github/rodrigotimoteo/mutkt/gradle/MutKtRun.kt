package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.AnalysisConfig
import com.github.rodrigotimoteo.mutkt.application.ClassFilters
import com.github.rodrigotimoteo.mutkt.application.Glob
import com.github.rodrigotimoteo.mutkt.application.RunMutationAnalysis
import com.github.rodrigotimoteo.mutkt.application.port.ReportWriter
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutionStopped
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.junit.ForkSettings
import com.github.rodrigotimoteo.mutkt.junit.ForkedTestExecutor
import com.github.rodrigotimoteo.mutkt.junit.derivedEngineClasspath
import com.github.rodrigotimoteo.mutkt.report.ConsoleReportWriter
import com.github.rodrigotimoteo.mutkt.report.FileSourceLookup
import com.github.rodrigotimoteo.mutkt.report.StrykerReportWriter
import com.github.rodrigotimoteo.mutkt.report.deadlineWarning
import org.gradle.api.GradleException
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

internal fun MutKtTask.runAnalysis(): AnalysisResult {
    checkThresholdRange(failBelowScore.orNull)
    val production = stage(classesDirs.files, File(temporaryDir, "classes"))
    val tests = stage(testClassesDirs.files, File(temporaryDir, "test-classes"))
    val projectClasses = projectClasses(production)
    val classDirs = classesDirs.files + testClassesDirs.files + projectSupportClassesDirs.files
    // Class directories reach the child through the staged copies, so only jars and resource directories remain.
    val dependencies = testRuntimeClasspath.files.filterNot { it in classDirs }.map(File::toPath)
    val config = analysisConfig()
    val summary = StringBuilder()
    val json = StringBuilder()
    val html = StringBuilder()
    val lookup = FileSourceLookup(sourceDirs.files.map(File::toPath))
    val writers = listOf(ConsoleReportWriter(summary, lookup), StrykerReportWriter(json, html, lookup))
    val writer =
        object : ReportWriter {
            override fun write(report: MutationReport) = writers.forEach { it.write(report) }
        }
    // The generator resolves supertypes of the analysed classes, including the ones the filters left out. The compile
    // classpath comes last, so runtime versions win and compileOnly types still resolve for frame computation.
    val compileOnly = compileClasspath.files.map(File::toPath)
    val urls = (listOf(production.dir) + dependencies + compileOnly).map { it.toUri().toURL() }.toTypedArray()
    val report =
        URLClassLoader(urls, null).use { loader ->
            // Built first so an unknown operator id fails before any child JVM could start.
            val generator = generator(loader)
            ForkedTestExecutor(forkSettings(projectClasses, tests, dependencies, config.parallelism)).use { executor ->
                try {
                    RunMutationAnalysis(DirectoryClassSource(production.dir), generator, executor, writer, config)
                        // Logged as soon as the baseline ends so a long run shows the hint before its mutants.
                        .run(onDeadlineCap = { logger.warn(deadlineWarning(it)) })
                } catch (e: TestExecutionStopped) {
                    throw GradleException(e.message.orEmpty(), e)
                }
            }
        }
    return AnalysisResult(
        summary.toString(),
        report.score,
        report.results.size,
        report.results.count { it.status == MutantStatus.ERROR },
        json.toString(),
        html.toString(),
    )
}

private fun MutKtTask.generator(loader: ClassLoader) =
    try {
        AsmMutantGenerator(operators.get(), { logger.warn(it) }) { name -> loader.classBytes(name) }
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
    production: Staged,
    tests: Staged,
    dependencies: List<Path>,
    parallelism: Int,
) = ForkSettings(
    javaExecutable = Path.of(testJavaExecutable.get()),
    jvmArgs = testJvmArgs.get(),
    // The child inherits the daemon's environment; the test task's own entries are deliberately not forwarded.
    environment = emptyMap(),
    workingDir = Path.of(testWorkingDir.get()),
    projectClassDir = production.dir,
    testClassDir = tests.dir,
    classpath = dependencies,
    parallelism = parallelism,
    engineClasspath = derivedEngineClasspath(),
    engines = engines.get().toList(),
    includeTags = includeTags.get().toList(),
    excludeTags = excludeTags.get().toList(),
    // Tools that map a code source back to sources (strikt's filepeek) only know Gradle's output directories.
    // A test class named like a main class wins, as it does in the child's loader.
    codeSources = production.origins + tests.origins,
)

// Support classes from the base loader would call the original classes (or none: the main jar may be missing),
// so the child JVMs load them next to the main classes; only the main classes are mutated.
private fun MutKtTask.projectClasses(production: Staged): Staged {
    val support = projectSupportClassesDirs.files
    if (support.isEmpty()) return production
    checkNoSharedClasses(classesDirs.files, support)
    return stage(classesDirs.files + support, File(temporaryDir, "project-classes"))
}
