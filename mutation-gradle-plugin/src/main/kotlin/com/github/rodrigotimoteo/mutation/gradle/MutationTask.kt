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
import org.gradle.api.tasks.InputFile
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
    @InputFile
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

    /** Fail build if mutation score below this threshold (0-100). */
    @Input
    @Optional
    val failOnScoreThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /** Fail build if line coverage below this threshold (0-100). */
    @Input
    @Optional
    val failOnCoverageThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /** Maximum mutations per class. 0 = no limit. */
    @Input
    @Optional
    val maxMutationsPerClass: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /** Enable incremental analysis (cache mutation results). */
    @Input
    @Optional
    val enableIncrementalAnalysis: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Timeout per mutant in milliseconds (separate from per-test timeout). */
    @Input
    @Optional
    val mutantTimeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(10000)

    /** Target classes to mutate (regex patterns). */
    @Input
    @Optional
    val targetClassPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /** Target tests to run (regex patterns). */
    @Input
    @Optional
    val targetTestPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /** Exclude classes matching these patterns. */
    @Input
    @Optional
    val excludeClassPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /** Exclude tests matching these patterns. */
    @Input
    @Optional
    val excludeTestPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /** Enable subsumption detection (skip redundant mutants). */
    @Input
    @Optional
    val enableSubsumption: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Enable weak mutant detection (skip mutations never reached). */
    @Input
    @Optional
    val enableWeakMutation: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Enable inlined finally detection. */
    @Input
    @Optional
    val enableInlinedFinally: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Enable test ordering (run most effective tests first). */
    @Input
    @Optional
    val enableTestOrdering: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Whether to run JaCoCo agent automatically. */
    @Input
    @Optional
    val autoRunJaCoCo: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Generate per-class mutation scores in reports. */
    @Input
    @Optional
    val showClassScores: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /** Generate interactive HTML graph of test-mutant relationships. */
    @Input
    @Optional
    val generateGraph: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /** Enable file-based caching of mutation results. */
    @Input
    @Optional
    val enableCache: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    @TaskAction
    fun runMutationTests() {
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("  Kotlin Mutation Testing - PITest-style")
        logger.lifecycle("=".repeat(60))

        // Find classes directories from file collections
        val classesDir = findClassesDir(targetClasses.files)
        val testClassesDir = findClassesDir(testClasses.files, isTestClasses = true)
        val classpathFiles = classpath.files.toList()

        // Find coverage file
        val coverageFile =
            if (coverageExecFile.isPresent) {
                coverageExecFile.get().asFile
            } else {
                // Auto-detect coverage file using layout.buildDirectory (Gradle 9.x safe)
                val buildDirFile = project.layout.buildDirectory.asFile.get()
                val possiblePaths =
                    listOf(
                        File(buildDirFile, "jacoco/test.exec"),
                        File(buildDirFile, "jacoco/jacocoTestReport.exec"),
                        File(buildDirFile, "reports/jacoco/test/jacocoTestReport.exec"),
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
            throw IllegalStateException("Classes directory not found: $classesDir")
        }

        if (!testClassesDir.exists()) {
            logger.error("Test classes directory not found: $testClassesDir")
            logger.error("Run 'gradlew compileTestKotlin' first")
            throw IllegalStateException("Test classes directory not found: $testClassesDir")
        }

        // Parse operators
        val operators = parseOperators(enabledOperators.getOrElse(emptySet()))
        logger.lifecycle("Enabled operators: ${operators.joinToString { it.operatorName }}")
        logger.lifecycle("Timeout: ${timeoutMs.get()}ms, Max parallel mutants: ${maxParallelMutants.get()}")

        // Create runner with all new features
        // Merge excludedClasses (extension defaults, glob patterns) with excludeClassPatterns (user regex)
        val excludeRegexPatterns = excludeClassPatterns.getOrElse(emptySet()).toList()
        val excludeGlobPatterns =
            excludedClasses.getOrElse(emptySet()).toList().map { glob ->
                // Convert glob patterns to regex: **/*.Test → .*.Test, *Test → .*Test
                glob.replace("**/", ".*").replace("*", ".*").replace("?", ".")
            }
        val allExcludePatterns = excludeRegexPatterns + excludeGlobPatterns

        // Use mutantTimeoutMs if set, otherwise timeoutMs
        val effectiveTimeout =
            if (mutantTimeoutMs.isPresent && mutantTimeoutMs.get() > 0) {
                mutantTimeoutMs.get()
            } else {
                timeoutMs.get()
            }

        val runner =
            MutationTestRunnerFactory.create(
                timeoutMs = effectiveTimeout,
                maxParallelMutants = maxParallelMutants.get(),
                enabledOperators = operators.toSet(),
                enableInlinedFinally = enableInlinedFinally.get(),
                enableTestOrdering = enableTestOrdering.get(),
                includePatterns = targetClassPatterns.getOrElse(emptySet()).toList(),
                excludePatterns = allExcludePatterns,
                enableCache = enableCache.get(),
                enableSubsumption = enableSubsumption.get(),
                enableWeakMutation = enableWeakMutation.get(),
                projectDir = project.projectDir,
            )

        // Run mutation testing
        val report =
            runner.run(
                classesDir = classesDir,
                testClassesDir = testClassesDir,
                classpath = classpathFiles,
                coverageExecFile = coverageFile,
            )

        // Generate reports
        generateReports(report)

        // Print summary
        printSummary(report)

        // Fail build if configured
        if (failOnSurvived.get() && report.survivedMutations > 0) {
            throw org.gradle.api.GradleException("${report.survivedMutations} mutants survived! Build failed.")
        }

        val threshold = failOnScoreThreshold.get()
        if (threshold > 0 && report.killedPercentage < threshold) {
            throw org.gradle.api.GradleException(
                "Mutation score ${report.killedPercentage}% is below threshold $threshold%. Build failed.",
            )
        }
    }

    private fun parseOperators(names: Set<String>): List<MutationOperator> {
        if (names.isEmpty()) return MutationOperator.MVP_OPERATORS.toList()
        val valid = mutableListOf<MutationOperator>()
        for (name in names) {
            val op = MutationOperator.fromName(name)
            if (op != null) {
                valid.add(op)
            } else {
                logger.warn("Unknown mutation operator: '$name' (ignored)")
            }
        }
        if (valid.isEmpty()) {
            logger.warn("All specified operators are invalid: $names. Falling back to MVP_OPERATORS.")
            return MutationOperator.MVP_OPERATORS.toList()
        }
        return valid
    }

    private fun findClassesDir(
        files: Set<File>,
        isTestClasses: Boolean = false,
    ): File {
        // Find the first directory that contains .class files
        for (file in files) {
            if (file.isDirectory) {
                val hasClasses = file.walkTopDown().maxDepth(10).any { it.extension == "class" }
                if (hasClasses) return file
            }
        }
        // Fallback to build directory — use layout.buildDirectory (Gradle 9.x safe)
        val subDir = if (isTestClasses) "test" else "main"
        val buildDirFile = project.layout.buildDirectory.asFile.get()
        return File(buildDirFile, "classes/kotlin/$subDir")
            .takeIf { it.exists() }
            ?: File(buildDirFile, "classes/java/$subDir")
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
                    logger.lifecycle(consoleReport)
                }
                "csv" -> {
                    val csvReport = com.github.rodrigotimoteo.mutation.report.CsvReportGenerator.generate(report, outputDirFile)
                    logger.lifecycle("CSV report: $csvReport")
                }
                "xml" -> {
                    val xmlReport = com.github.rodrigotimoteo.mutation.report.XmlReportGenerator.generate(report, outputDirFile)
                    logger.lifecycle("XML report: $xmlReport")
                }
                "json" -> {
                    val jsonReport = com.github.rodrigotimoteo.mutation.report.JsonReportGenerator.generate(report, outputDirFile)
                    logger.lifecycle("JSON report: $jsonReport")
                }
                "graph" -> {
                    val graphReport = com.github.rodrigotimoteo.mutation.report.MutationGraphGenerator.generate(report, outputDirFile)
                    logger.lifecycle("Graph report: $graphReport")
                }
                else -> {
                    logger.warn("Unknown report format: $format (supported: html, console, csv, xml, json, graph)")
                }
            }
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
        logger.lifecycle("Total time:       ${"%.1f".format(report.totalExecutionTimeMs / 1000.0)}s")
        logger.lifecycle("=".repeat(60))
    }
}
