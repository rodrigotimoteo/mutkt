package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import com.github.rodrigotimoteo.mutation.LOG_PREFIX
import com.github.rodrigotimoteo.mutation.REPORT_WIDTH
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.runner.MutationTestRunnerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

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
 *
 * Configuration-cache safe: uses injected [ProjectLayout] and [ObjectFactory]
 * services. No `Task.project` access at execution time.
 */
abstract class MutationTask
    @Inject
    constructor(
        projectLayout: ProjectLayout,
        objectFactory: ObjectFactory,
    ) : DefaultTask() {
        /**
         * Project directory captured at configuration time. Safe to read at
         * execution time without touching `Task.project`.
         */
        @get:Internal
        val projectDirectory: Directory = projectLayout.projectDirectory

        /**
         * Build directory reference captured at configuration time. The underlying
         * [DirectoryProperty] is resolved at execution time, but the property
         * itself is obtained during configuration via the injected [ProjectLayout].
         */
        @get:Internal
        val buildDirectory: DirectoryProperty = projectLayout.buildDirectory

        /** Target classes to mutate. Auto-detected from sourceSets.main.output. */
        @InputFiles
        @Optional
        val targetClasses: ConfigurableFileCollection = objectFactory.fileCollection()

        /** Test classes to run against mutations. Auto-detected from sourceSets.test.output. */
        @InputFiles
        @Optional
        val testClasses: ConfigurableFileCollection = objectFactory.fileCollection()

        /** Classpath for test execution. Auto-detected from testRuntimeClasspath. */
        @InputFiles
        @Optional
        val classpath: ConfigurableFileCollection = objectFactory.fileCollection()

        /** JaCoCo execution file for coverage analysis. */
        @InputFile
        @Optional
        val coverageExecFile: RegularFileProperty = objectFactory.fileProperty()

        /** Mutation operators to enable. Defaults to MVP_OPERATORS. */
        @Input
        @Optional
        val enabledOperators: SetProperty<String> = objectFactory.setProperty(String::class.java)

        /** Timeout in milliseconds for each mutant test execution. */
        @Input
        @Optional
        val timeoutMs: Property<Long> = objectFactory.property(Long::class.java).convention(DEFAULT_TIMEOUT_MS)

        /** Number of parallel mutant test executions. */
        @Input
        @Optional
        val maxParallelMutants: Property<Int> = objectFactory.property(Int::class.java).convention(4)

        /** Report formats to generate: "html", "console". */
        @Input
        @Optional
        val reportFormats: SetProperty<String> =
            objectFactory.setProperty(String::class.java)
                .convention(setOf("html"))

        /** Output directory for mutation test reports. */
        @OutputDirectory
        val reportsDir: DirectoryProperty =
            objectFactory.directoryProperty()
                .convention(projectLayout.buildDirectory.dir("reports/mutation"))

        /**
         * Directory holding MutKt state (cache, baseline, kill-sets, test-strength,
         * incremental analysis). Declared as task output so Gradle's build cache
         * is aware of writes performed by `IncrementalAnalyzer`, `MutKtCache`,
         * `BaselineStorage`, `KillSetStorage`, and `TestStrengthOrdering`.
         */
        @OutputDirectory
        val mutktDir: DirectoryProperty =
            objectFactory.directoryProperty()
                .convention(projectLayout.projectDirectory.dir(".mutkt"))

        /** Whether to fail the build if any mutants survive. */
        @Input
        @Optional
        val failOnSurvived: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

        /** Classes to exclude from mutation testing. */
        @Input
        @Optional
        val excludedClasses: SetProperty<String> = objectFactory.setProperty(String::class.java)

        /** Methods to exclude from mutation testing. */
        @Input
        @Optional
        val excludedMethods: SetProperty<String> = objectFactory.setProperty(String::class.java)

        /** Fail build if mutation score below this threshold (0-100). */
        @Input
        @Optional
        val failOnScoreThreshold: Property<Int> = objectFactory.property(Int::class.java).convention(0)

        /** Fail build if mutation score below this threshold (0-100). */
        @Input
        @Optional
        val failOnMutationScoreThreshold: Property<Int> = objectFactory.property(Int::class.java).convention(0)

        /** Maximum mutations per class. 0 = no limit. */
        @Input
        @Optional
        val maxMutationsPerClass: Property<Int> = objectFactory.property(Int::class.java).convention(0)

        /** Enable incremental analysis (cache mutation results). */
        @Input
        @Optional
        val enableIncrementalAnalysis: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Timeout per mutant in milliseconds (separate from per-test timeout). */
        @Input
        @Optional
        val mutantTimeoutMs: Property<Long> = objectFactory.property(Long::class.java).convention(0)

        /** Target classes to mutate (regex patterns). */
        @Input
        @Optional
        val targetClassPatterns: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** Target tests to run (regex patterns). */
        @Input
        @Optional
        val targetTestPatterns: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** Exclude classes matching these patterns. */
        @Input
        @Optional
        val excludeClassPatterns: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** Exclude tests matching these patterns. */
        @Input
        @Optional
        val excludeTestPatterns: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** Enable subsumption detection (skip redundant mutants). */
        @Input
        @Optional
        val enableSubsumption: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Enable weak mutant detection (skip mutations never reached). */
        @Input
        @Optional
        val enableWeakMutation: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Enable inlined finally detection. */
        @Input
        @Optional
        val enableInlinedFinally: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Enable test ordering (run most effective tests first). */
        @Input
        @Optional
        val enableTestOrdering: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Whether to run JaCoCo agent automatically. */
        @Input
        @Optional
        val autoRunJaCoCo: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Generate per-class mutation scores in reports. */
        @Input
        @Optional
        val showClassScores: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

        /** Generate interactive HTML graph of test-mutant relationships. */
        @Input
        @Optional
        val generateGraph: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

        /** Enable file-based caching of mutation results. */
        @Input
        @Optional
        val enableCache: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

        /** Target specific packages for mutation testing. */
        @Input
        @Optional
        val targetPackages: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** Exclude specific packages from mutation testing. */
        @Input
        @Optional
        val excludePackages: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(emptySet())

        /** CI mode: generate machine-readable reports and exit with proper codes. */
        @Input
        @Optional
        val ciMode: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

        /** Verbose output: show all mutations tested. */
        @Input
        @Optional
        val verbose: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

        @TaskAction
        fun runMutationTests() {
            // CI mode: ensure console report is enabled
            if (ciMode.get()) {
                val currentFormats = reportFormats.getOrElse(emptySet()).toMutableSet()
                currentFormats.add("console")
                currentFormats.add("xml")
                reportFormats.set(currentFormats)
                logger.lifecycle("$LOG_PREFIX CI mode enabled — console + XML reports will be generated")
            }

            logger.lifecycle("=".repeat(REPORT_WIDTH))
            logger.lifecycle("  Kotlin Mutation Testing - PITest-style")
            logger.lifecycle("=".repeat(REPORT_WIDTH))

            // Find classes directories from file collections
            val classesDir = findClassesDir(targetClasses.files)
            val testClassesDir = findClassesDir(testClasses.files, isTestClasses = true)
            val classpathFiles = classpath.files.toList()

            // Find coverage file
            val coverageFile =
                if (coverageExecFile.isPresent) {
                    coverageExecFile.get().asFile
                } else {
                    // Auto-detect coverage file using buildDirectory captured at config time
                    val buildDirFile = buildDirectory.asFile.get()
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
                val msg =
                    buildString {
                        appendLine("Classes directory not found: $classesDir")
                        appendLine("Fix: Run 'gradlew compileKotlin' (or 'compileKotlinJvm' for KMP) first, or set targetClasses manually:")
                        appendLine("  mutationTest {")
                        appendLine("    targetClasses.from(file(\"build/classes/kotlin/main\"))")
                        appendLine("  }")
                    }
                logger.error(msg)
                throw IllegalStateException("Classes directory not found: $classesDir")
            }

            if (!testClassesDir.exists()) {
                val msg =
                    buildString {
                        appendLine("Test classes directory not found: $testClassesDir")
                        appendLine("Fix: Run 'gradlew compileTestKotlin' first, or set testClasses manually:")
                        appendLine("  mutationTest {")
                        appendLine("    testClasses.from(file(\"build/classes/kotlin/test\"))")
                        appendLine("  }")
                    }
                logger.error(msg)
                throw IllegalStateException("Test classes directory not found: $testClassesDir")
            }

            if (classpathFiles.isEmpty()) {
                logger.warn("Classpath is empty. Tests may fail to load. Ensure test dependencies are configured.")
            }

            // Parse operators
            val operators = parseOperators(enabledOperators.getOrElse(emptySet()))
            logger.lifecycle("Enabled operators: ${operators.joinToString { it.operatorName }}")
            logger.lifecycle("Timeout: ${timeoutMs.get()}ms, Max parallel mutants: ${maxParallelMutants.get()}")

            // Build include/exclude patterns from package targeting
            val includePatterns = targetClassPatterns.getOrElse(emptySet()).toMutableSet()
            val targetPkgs = targetPackages.getOrElse(emptySet())
            if (targetPkgs.isNotEmpty()) {
                targetPkgs.forEach { pkg ->
                    includePatterns.add("${pkg.replace(".", "\\.")}\\..*")
                }
                logger.lifecycle("Target packages: ${targetPkgs.joinToString()}")
            }

            val excludeRegexPatterns = excludeClassPatterns.getOrElse(emptySet()).toMutableSet()
            val excludePkgs = excludePackages.getOrElse(emptySet())
            if (excludePkgs.isNotEmpty()) {
                excludePkgs.forEach { pkg ->
                    excludeRegexPatterns.add("${pkg.replace(".", "\\.")}\\..*")
                }
                logger.lifecycle("Exclude packages: ${excludePkgs.joinToString()}")
            }

            // Create runner with all new features
            // Merge excludedClasses (extension defaults, glob patterns) with excludeClassPatterns (user regex)
            val excludeGlobPatterns =
                excludedClasses.getOrElse(emptySet()).toList().map { glob ->
                    globToRegex(glob)
                }
            val allExcludePatterns = (excludeRegexPatterns + excludeGlobPatterns).toList()

            // Compute changed classes for incremental analysis
            val changedClasses =
                if (enableIncrementalAnalysis.get()) {
                    try {
                        val incrementalAnalyzer =
                            com.github.rodrigotimoteo.mutation.engine.IncrementalAnalyzer(
                                projectDirectory.asFile,
                            )
                        incrementalAnalyzer.getChangedClasses()
                    } catch (e: Exception) {
                        logger.warn("Incremental analysis failed: ${e.message}")
                        emptySet()
                    }
                } else {
                    emptySet()
                }
            if (changedClasses.isNotEmpty()) {
                logger.lifecycle("Incremental: ${changedClasses.size} changed classes detected")
            }

            // Use mutantTimeoutMs if explicitly set (> 0), otherwise timeoutMs
            val effectiveTimeout =
                if (mutantTimeoutMs.getOrElse(0) > 0) {
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
                    includePatterns = includePatterns.toList(),
                    excludePatterns = allExcludePatterns,
                    enableCache = enableCache.get(),
                    enableSubsumption = enableSubsumption.get(),
                    enableWeakMutation = enableWeakMutation.get(),
                    projectDir = mutktDir.asFile.get(),
                    excludedMethods = excludedMethods.getOrElse(emptySet()),
                    maxMutationsPerClass = maxMutationsPerClass.get(),
                    targetTestPatterns = targetTestPatterns.getOrElse(emptySet()).toList(),
                    excludeTestPatterns = excludeTestPatterns.getOrElse(emptySet()).toList(),
                    changedClasses = changedClasses,
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
            checkFailConditions(report)

            // Auto-add graph to report formats if generateGraph is set
            if (generateGraph.get()) {
                val currentFormats = reportFormats.getOrElse(emptySet()).toMutableSet()
                currentFormats.add("graph")
                reportFormats.set(currentFormats)
                logger.lifecycle("$LOG_PREFIX Graph report enabled via generateGraph option")
            }
        }

        /**
         * Enforce configured build-failure thresholds against a finished report.
         * Throws [org.gradle.api.GradleException] when any threshold is violated.
         * Extracted from [runMutationTests] so it can be exercised in isolation
         * without spinning up the full mutation runner.
         */
        internal fun checkFailConditions(report: MutationReport) {
            if (failOnSurvived.get() && report.survivedMutations > 0) {
                throw org.gradle.api.GradleException(
                    "${report.survivedMutations} mutants survived! Build failed.",
                )
            }

            val threshold = failOnScoreThreshold.get()
            if (threshold > 0 && report.killedPercentage < threshold) {
                throw org.gradle.api.GradleException(
                    "Mutation score ${report.killedPercentage}% is below threshold $threshold%. Build failed.",
                )
            }

            val coverageThreshold = failOnMutationScoreThreshold.get()
            if (coverageThreshold > 0 && report.killedPercentage < coverageThreshold) {
                throw org.gradle.api.GradleException(
                    "Mutation score ${report.killedPercentage}% is below threshold $coverageThreshold%. Build failed.",
                )
            }
        }

        private fun globToRegex(glob: String): String {
            return glob
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("**", " ")
                .replace("*", "[^/]*")
                .replace(" ", ".*")
                .replace("?", ".")
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
            // Fallback to build directory — use buildDirectory captured at config time
            val subDir = if (isTestClasses) "test" else "main"
            val buildDirFile = buildDirectory.asFile.get()
            return File(buildDirFile, "classes/kotlin/$subDir")
                .takeIf { it.exists() }
                ?: File(buildDirFile, "classes/kotlin/jvm/$subDir")
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
                        val htmlReport = com.github.rodrigotimoteo.mutation.report.HtmlReportGenerator().generate(report, outputDirFile)
                        logger.lifecycle("HTML report: $htmlReport")
                    }
                    "console" -> {
                        val consoleReport = com.github.rodrigotimoteo.mutation.report.ConsoleReporter().generate(report)
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
            logger.lifecycle("=".repeat(REPORT_WIDTH))
            logger.lifecycle("  Mutation Test Results")
            logger.lifecycle("=".repeat(REPORT_WIDTH))
            logger.lifecycle("Total mutations:  ${report.totalMutations}")
            logger.lifecycle("Killed:           ${report.killedMutations} (${report.killedPercentage}%)")
            logger.lifecycle("Survived:         ${report.survivedMutations} (${report.survivedPercentage}%)")
            logger.lifecycle("Errors:           ${report.errorMutations}")
            logger.lifecycle("Timeouts:         ${report.timeoutMutations}")
            logger.lifecycle("No coverage:      ${report.noCoverageMutations}")
            logger.lifecycle("Total time:       ${"%.1f".format(report.totalExecutionTimeMs / 1000.0)}s")
            logger.lifecycle("=".repeat(REPORT_WIDTH))
        }
    }
