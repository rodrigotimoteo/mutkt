package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.runner.MutationTestRunnerFactory
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*

import java.io.File

/**
 * Gradle task for running mutation testing analysis.
 * This is the main entry point - just add the plugin and run `gradlew mutationTest`.
 */
abstract class MutationTask : DefaultTask() {

    @InputFiles
    @Optional
    val targetClasses: ConfigurableFileCollection = project.objects.fileCollection()

    @InputFiles
    @Optional
    val testClasses: ConfigurableFileCollection = project.objects.fileCollection()

    @InputFiles
    @Optional
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    @InputFile
    @Optional
    val coverageExecFile: RegularFileProperty = project.objects.fileProperty()

    @Input
    @Optional
    val enabledOperators: SetProperty<String> = project.objects.setProperty(String::class.java)

    @Input
    @Optional
    val timeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(30000)

    @Input
    @Optional
    val maxParallelMutants: Property<Int> = project.objects.property(Int::class.java).convention(4)

    @Input
    @Optional
    val reportFormat: Property<String> = project.objects.property(String::class.java).convention("html")

    @Input
    @Optional
    val outputDir: Property<String> = project.objects.property(String::class.java)
        .convention("build/reports/mutation")

    @Input
    @Optional
    val failOnSurvived: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    @Input
    @Optional
    val excludedClasses: SetProperty<String> = project.objects.setProperty(String::class.java)

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
        val coverageFile = if (coverageExecFile.isPresent) {
            coverageExecFile.get().asFile
        } else {
            // Auto-detect JaCoCo coverage file
            val possiblePaths = listOf(
                File(project.buildDir, "jacoco/test.exec"),
                File(project.buildDir, "jacoco/jacocoTestReport.exec"),
                File(project.buildDir, "reports/jacoco/test/jacocoTestReport.exec")
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
        val runner = MutationTestRunnerFactory.create(
            timeoutMs = timeoutMs.get(),
            maxParallelMutants = maxParallelMutants.get(),
            enabledOperators = operators.toSet()
        )

        // Run mutation testing
        val report = runner.run(
            classesDir = classesDir,
            testClassesDir = testClassesDir,
            classpath = classpathFiles,
            coverageExecFile = coverageFile,
            enabledOperators = operators.toSet()
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
                val hasClasses = file.walkTopDown().any { it.extension == "class" }
                if (hasClasses) return file
            }
        }
        // Fallback to build directory
        return File(project.buildDir, "classes/kotlin/main")
    }

    private fun generateReports(report: MutationReport) {
        val outputDirPath = outputDir.get()
        val outputDirFile = File(project.rootDir, outputDirPath)
        outputDirFile.mkdirs()

        // Generate HTML report
        val htmlReport = HtmlReportGenerator.generate(report, outputDirFile)
        logger.lifecycle("HTML report: $htmlReport")

        // Generate console report
        val consoleReport = ConsoleReportGenerator.generate(report)
        println(consoleReport)
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