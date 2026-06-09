package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * Gradle plugin for Kotlin Mutation Testing.
 *
 * Adds `mutationTest` task that runs PITest-style mutation testing.
 * Zero-config: just apply the plugin and run `./gradlew mutationTest`.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("com.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
 * }
 * ```
 *
 * Custom configuration:
 * ```kotlin
 * mutationTest {
 *     timeoutMs.set(60000)
 *     maxParallelMutants.set(8)
 *     reportFormats.set(setOf("html", "console"))
 *     failOnScoreThreshold.set(70)
 * }
 * ```
 */
class MutationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register extension for DSL configuration
        val extension =
            project.extensions.create(
                "mutationTest",
                MutationPluginExtension::class.java,
                project,
            )

        // Register mutation test task with lazy property wiring
        val mutationTask =
            project.tasks.register(
                "mutationTest",
                MutationTask::class.java,
            ) { task ->
                task.group = "verification"
                task.description = "Runs mutation testing analysis"
                task.dependsOn("compileKotlin", "compileTestKotlin")

                // Wire extension properties lazily
                task.targetClasses.from(extension.targetClasses)
                task.testClasses.from(extension.testClasses)
                task.classpath.from(extension.classpath)
                task.enabledOperators.set(extension.enabledOperators)
                task.timeoutMs.set(extension.timeoutMs)
                task.maxParallelMutants.set(extension.maxParallelMutants)
                task.reportFormats.set(extension.reportFormats)
                task.failOnSurvived.set(extension.failOnSurvived)
                task.excludedClasses.set(extension.excludedClasses)
                task.excludedMethods.set(extension.excludedMethods)
                task.failOnScoreThreshold.set(extension.failOnScoreThreshold)
                task.coverageExecFile.set(extension.coverageExecFile)
                task.reportsDir.set(
                    extension.outputDir.map { dir ->
                        project.layout.projectDirectory.dir(dir)
                    },
                )
                // Wire newly added extension properties
                task.failOnCoverageThreshold.set(extension.failOnCoverageThreshold)
                task.maxMutationsPerClass.set(extension.maxMutationsPerClass)
                task.enableIncrementalAnalysis.set(extension.enableIncrementalAnalysis)
                task.mutantTimeoutMs.set(extension.mutantTimeoutMs)
                task.targetClassPatterns.set(extension.targetClassPatterns)
                task.targetTestPatterns.set(extension.targetTestPatterns)
                task.excludeClassPatterns.set(extension.excludeClassPatterns)
                task.excludeTestPatterns.set(extension.excludeTestPatterns)
                task.enableSubsumption.set(extension.enableSubsumption)
                task.enableWeakMutation.set(extension.enableWeakMutation)
                task.enableInlinedFinally.set(extension.enableInlinedFinally)
                task.enableTestOrdering.set(extension.enableTestOrdering)
                task.autoRunJaCoCo.set(extension.autoRunJaCoCo)
                task.showClassScores.set(extension.showClassScores)
                task.generateGraph.set(extension.generateGraph)
                task.enableCache.set(extension.enableCache)
                task.targetPackages.set(extension.targetPackages)
                task.excludePackages.set(extension.excludePackages)
                task.ciMode.set(extension.ciMode)
                task.verbose.set(extension.verbose)
            }

        // Auto-detect sourceSets after project evaluation
        project.afterEvaluate {
            autoDetectSourceSets(project, extension)
            autoDetectClasspath(project, extension)
            autoDetectJaCoCo(project, extension)
        }

        // Don't auto-depend on check - mutationTest is opt-in
        // Users run: ./gradlew mutationTest
    }

    private fun autoDetectSourceSets(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

            // Auto-detect main source set output
            val mainSourceSet = sourceSets.findByName("main")
            if (mainSourceSet != null) {
                extension.targetClasses.from(mainSourceSet.output)
                project.logger.info("Auto-detected target classes from sourceSets.main.output")
            }

            // Auto-detect test source set output
            val testSourceSet = sourceSets.findByName("test")
            if (testSourceSet != null) {
                extension.testClasses.from(testSourceSet.output)
                project.logger.info("Auto-detected test classes from sourceSets.test.output")
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect sourceSets: ${e.message}")
        }
    }

    private fun autoDetectClasspath(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            // Auto-detect testRuntimeClasspath (already includes main output)
            val testRuntimeClasspath = project.configurations.findByName("testRuntimeClasspath")
            if (testRuntimeClasspath != null) {
                extension.classpath.from(testRuntimeClasspath)
                project.logger.info("Auto-detected classpath from testRuntimeClasspath")
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect classpath: ${e.message}")
        }
    }

    private fun autoDetectJaCoCo(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            // Check if JaCoCo plugin is applied
            val jacocoPlugin = project.plugins.findPlugin("jacoco")
            if (jacocoPlugin != null) {
                project.logger.info("JaCoCo plugin detected, auto-configuring coverage analysis")

                // Auto-detect JaCoCo report task output
                val jacocoReportTask = project.tasks.findByName("jacocoTestReport")
                if (jacocoReportTask != null) {
                    extension.coverageExecFile.set(
                        project.layout.buildDirectory.file("jacoco/test.exec"),
                    )
                    project.logger.info("Auto-detected JaCoCo coverage file")
                }
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect JaCoCo: ${e.message}")
        }
    }
}
