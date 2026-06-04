package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.runner.MutationTestRunnerFactory
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

import java.io.File

/**
 * Gradle task for running mutation testing analysis.
 * This is the main entry point - just add the plugin and run `gradlew mutationTest`.
 */
abstract class MutationTask : DefaultTask() {

    @Input
    @Optional
    val enabledOperators = project.objects.listProperty(String::class.java)

    @Input
    @Optional
    val timeoutMs = project.objects.property(Long::class.java).convention(30000)

    @Input
    @Optional
    val maxParallelMutants = project.objects.property(Int::class.java).convention(4)

    @Input
    @Optional
    val reportFormat = project.objects.property(String::class.java).convention("console")

    @Input
    @Optional
    val outputDir = project.objects.property(File::class.java).convention(
        File(project.buildDir, "reports/mutation-test")
    )

    @TaskAction
    fun runMutationTests() {
        logger.lifecycle("=" .repeat(60))
        logger.lifecycle("  Kotlin Mutation Testing - PITest-style")
        logger.lifecycle("=" .repeat(60))

        // Parse operators
        val operators = parseOperators(enabledOperators.getOrElse(emptyList()))
        logger.lifecycle("Enabled operators: ${operators.joinToString { it.operatorName }}")
        logger.lifecycle("Timeout: ${timeoutMs.get()}ms, Max parallel mutants: ${maxParallelMutants.get()}")

        // Find output directories
        val classesDir = findOutputDir("classes/kotlin", "classes/java")
        val testClassesDir = findOutputDir("test-classes", "classes/kotlin/test")
        val classpath = buildClasspath()

        logger.lifecycle("Classes dir: $classesDir")
        logger.lifecycle("Test classes dir: $testClassesDir")

        if (!classesDir.exists()) {
            logger.warn("Classes directory not found: $classesDir")
            return
        }

        if (!testClassesDir.exists()) {
            logger.warn("Test classes directory not found: $testClassesDir")
            return
        }

        // Create runner
        val runner = MutationTestRunnerFactory.create(
            timeoutMs = timeoutMs.get(),
            maxParallelMutants = maxParallelMutants.get(),
            enabledOperators = operators.toSet()
        )

        // Find coverage file if it exists
        val coverageFile = File(project.buildDir, "reports/jacoco/test/jacocoTestReport.exec")
        val coverageExecFile = if (coverageFile.exists()) coverageFile else null

        // Run mutation testing
        val report = runner.run(
            classesDir = classesDir,
            testClassesDir = testClassesDir,
            classpath = classpath,
            coverageExecFile = coverageExecFile,
            enabledOperators = operators.toSet()
        )

        // Generate reports
        generateReports(report)

        // Print summary
        printSummary(report)

        // Fail build if configured
        if (report.survivedMutations > 0) {
            logger.warn("${report.survivedMutations} mutants survived!")
        }
    }

    private fun parseOperators(names: List<String>): List<MutationOperator> {
        if (names.isEmpty()) return MutationOperator.MVP_OPERATORS.toList()
        return names.mapNotNull { MutationOperator.fromName(it) }
    }

    private fun findOutputDir(vararg candidates: String): File {
        for (candidate in candidates) {
            val dir = File(project.buildDir, candidate)
            if (dir.exists()) return dir
        }
        return File(project.buildDir, candidates.first())
    }

    private fun buildClasspath(): List<File> {
        val classpath = mutableListOf<File>()

        // Add compiled classes
        val classesDir = findOutputDir("classes/kotlin", "classes/java")
        if (classesDir.exists()) classpath.add(classesDir)

        val testClassesDir = findOutputDir("test-classes", "classes/kotlin/test")
        if (testClassesDir.exists()) classpath.add(testClassesDir)

        // Add all compile-time dependencies
        project.configurations.findByName("runtimeClasspath")?.forEach { file ->
            classpath.add(file)
        }

        project.configurations.findByName("testRuntimeClasspath")?.forEach { file ->
            if (!classpath.any { it.name == file.name }) {
                classpath.add(file)
            }
        }

        return classpath
    }

    private fun generateReports(report: MutationReport) {
        val outputDir = outputDir.get()
        outputDir.mkdirs()

        // Generate HTML report
        val htmlReport = HtmlReportGenerator.generate(report, outputDir)
        logger.lifecycle("HTML report: $htmlReport")

        // Generate console report
        val consoleReport = ConsoleReportGenerator.generate(report)
        println(consoleReport)
    }

    private fun printSummary(report: MutationReport) {
        logger.lifecycle("")
        logger.lifecycle("=" .repeat(60))
        logger.lifecycle("  Mutation Test Results")
        logger.lifecycle("=" .repeat(60))
        logger.lifecycle("Total mutations:  ${report.totalMutations}")
        logger.lifecycle("Killed:           ${report.killedMutations} (${report.killedPercentage}%)")
        logger.lifecycle("Survived:         ${report.survivedMutations} (${report.survivedPercentage}%)")
        logger.lifecycle("Errors:           ${report.errorMutations}")
        logger.lifecycle("Timeouts:         ${report.timeoutMutations}")
        logger.lifecycle("No coverage:      ${report.noCoverageMutations}")
        logger.lifecycle("Total time:       ${report.totalExecutionTimeMs / 1000}s")
        logger.lifecycle("=" .repeat(60))
    }
}