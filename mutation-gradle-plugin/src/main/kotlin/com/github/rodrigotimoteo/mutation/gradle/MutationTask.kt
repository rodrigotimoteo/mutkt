package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.runner.MutationTestRunnerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task for running mutation testing analysis.
 *
 * This is the main entry point - just add the plugin and run `gradlew mutationTest`.
 *
 * The task auto-detects:
 * - Source sets (src/main/kotlin, src/main/java)
 * - Classpath (test runtime dependencies)
 * - Kotlin vs Java compilation
 *
 * Example:
 * ```bash
 * ./gradlew mutationTest
 * ```
 */
abstract class MutationTask : DefaultTask() {
    /** Target classes to mutate. Auto-detected from sourceSets.main.output. */
    @InputFiles
    @Optional
    val targetClasses: ConfigurableFileCollection = project.objects.fileCollection()

    /** Test classes to run against mutations. Auto-detected from sourceSets.test.output. */
    @InputFiles
    @Optional
    val testClasses: ConfigurableFileCollection = project.objects.fileCollection()

    /** Classpath for test execution. Auto-detected from testRuntimeClasspath. */
    @InputFiles
    @Optional
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    /** JaCoCo execution file for coverage analysis. */
    @Input
    @Optional
    val coverageExecFile: RegularFileProperty = project.objects.fileProperty()

    /** Mutation operators to enable. Defaults to MVP_OPERATORS. */
    @Input
    @Optional
    val enabledOperators: SetProperty<String> = project.objects.setProperty(String::class.java)

    /** Timeout in milliseconds for each mutant test execution. */
    @Input
    @Optional
    val timeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(30000)

    /** Number of parallel mutant test executions. */
    @Input
    @Optional
    val maxParallelMutants: Property<Int> = project.objects.property(Int::class.java).convention(4)

    /** Report formats to generate: "html", "console". */
    @Input
    @Optional
    val reportFormats: SetProperty<String> =
        project.objects.setProperty(String::class.java)
            .convention(setOf("html"))

    /** Output directory for mutation test reports. */
    @OutputDirectory
    val reportsDir: DirectoryProperty =
        project.objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("reports/mutation"))

    /** Whether to fail the build if any mutants survive. */
    @Input
    @Optional
    val failOnSurvived: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /** Classes to exclude from mutation testing. */
    @Input
    @Optional
    val excludedClasses: SetProperty<String> = project.objects.setProperty(String::class.java)

    /** Methods to exclude from mutation testing. */
    @Input
    @Optional
    val excludedMethods: SetProperty<String> = project.objects.setProperty(String::class.java)

    @TaskAction
    fun runMutationTests() {
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("  Kotlin Mutation Testing - PITest-style")
        logger.lifecycle("=".repeat(60))

        // Find classes directories from file collections
        val classesDir = findClassesDir(targetClasses.files)
        val testClassesDir = findClassesDir(testClasses.files)
        val classpathFiles = classpath.files.toList()

        // Find coverage file
        val coverageFile =
            if (coverageExecFile.isPresent) {
                coverageExecFile.get().asFile
            } else {
                // Auto-detect coverage file
                val buildDir = reportsDir.get().asFile.parentFile?.parentFile ?: project.buildDir
                val possiblePaths =
                    listOf(
                        File(buildDir, "jacoco/test.exec"),
                        File(buildDir, "jacoco/jacocoTestReport.exec"),
                        File(buildDir, "reports/jacoco/test/jacocoTestReport.exec"),
                    )
                possiblePaths.firstOrNull { it.exists() }
            }

        logger.lifecycle("Classes dir: $classesDir")
        logger.lifecycle("Test classes dir: $testClassesDir")
        logger.lifecycle("Classpath size: ${classpathFiles.size}")
        if (coverageFile != null) {
            logger.lifecycle("Coverage file: $coverageFile")
        }

        // Validate directories exist
        if (!classesDir.exists()) {
            logger.error("Classes directory not found: $classesDir")
            logger.error("Run 'gradlew compileKotlin' first")
            return
        }

        if (!testClassesDir.exists()) {
            logger.error("Test classes directory not found: $testClassesDir")
            logger.error("Run 'gradlew compileTestKotlin' first")
            return
        }

        // Parse operators
        val operators = parseOperators(enabledOperators.getOrElse(emptySet()))
        logger.lifecycle("Enabled operators: ${operators.joinToString { it.operatorName }}")
        logger.lifecycle("Timeout: ${timeoutMs.get()}ms, Max parallel mutants: ${maxParallelMutants.get()}")

        // Create runner
        val runner =
            MutationTestRunnerFactory.create(
                timeoutMs = timeoutMs.get(),
                maxParallelMutants = maxParallelMutants.get(),
                enabledOperators = operators.toSet(),
            )

        // Run mutation testing
        val report =
            runner.run(
                classesDir = classesDir,
                testClassesDir = testClassesDir,
                classpath = classpathFiles,
                coverageExecFile = coverageFile,
                enabledOperators = operators.toSet(),
            )

        // Generate reports
        generateReports(report)

        // Print summary
        printSummary(report)

        // Fail build if configured
        if (failOnSurvived.get() && report.survivedMutations > 0) {
            throw RuntimeException("${report.survivedMutations} mutants survived! Build failed.")
        }
    }

    private fun parseOperators(names: Set<String>): List<MutationOperator> {
        if (names.isEmpty()) return MutationOperator.MVP_OPERATORS.toList()
        return names.mapNotNull { MutationOperator.fromName(it) }
    }

    private fun findClassesDir(files: Set<File>): File {
        // Find the first directory that contains .class files
        for (file in files) {
            if (file.isDirectory) {
                val hasClasses = file.walkTopDown().maxDepth(5).any { it.extension == "class" }
                if (hasClasses) return file
            }
        }
        // Fallback to build directory
        return File(project.buildDir, "classes/kotlin/main")
    }

    private fun generateReports(report: MutationReport) {
        val outputDirFile = reportsDir.get().asFile
        outputDirFile.mkdirs()

        val formats = reportFormats.get()

        for (format in formats) {
            when (format.lowercase()) {
                "html" -> {
                    val htmlReport = HtmlReportGenerator.generate(report, outputDirFile)
                    logger.lifecycle("HTML report: $htmlReport")
                }
                "console" -> {
                    val consoleReport = ConsoleReportGenerator.generate(report)
                    println(consoleReport)
                }
                else -> {
                    logger.warn("Unknown report format: $format (supported: html, console)")
                }
            }
        }

        // Always generate console output
        if (!formats.contains("console")) {
            val consoleReport = ConsoleReportGenerator.generate(report)
            println(consoleReport)
        }
    }

    private fun printSummary(report: MutationReport) {
        logger.lifecycle("")
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("  Mutation Test Results")
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("Total mutations:  ${report.totalMutations}")
        logger.lifecycle("Killed:           ${report.killedMutations} (${report.killedPercentage}%)")
        logger.lifecycle("Survived:         ${report.survivedMutations} (${report.survivedPercentage}%)")
        logger.lifecycle("Errors:           ${report.errorMutations}")
        logger.lifecycle("Timeouts:         ${report.timeoutMutations}")
        logger.lifecycle("No coverage:      ${report.noCoverageMutations}")
        logger.lifecycle("Total time:       ${report.totalExecutionTimeMs / 1000}s")
        logger.lifecycle("=".repeat(60))
    }
}
