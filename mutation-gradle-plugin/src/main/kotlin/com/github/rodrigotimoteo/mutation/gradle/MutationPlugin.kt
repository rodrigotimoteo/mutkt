package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * Gradle plugin for Kotlin Mutation Testing.
 * Adds `mutationTest` task that runs PITest-style mutation testing.
 *
 * Zero-config usage:
 * ```kotlin
 * plugins {
 *     id("com.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
 * }
 * // Run: ./gradlew mutationTest
 * ```
 *
 * Custom configuration:
 * ```kotlin
 * mutationTest {
 *     timeoutMs.set(60000)
 *     maxParallelMutants.set(8)
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

        // Auto-detect sourceSets after project evaluation
        project.afterEvaluate {
            autoDetectSourceSets(project, extension)
            autoDetectJaCoCo(project, extension)
            autoDetectClasspath(project, extension)
        }

        // Register mutation test task
        val mutationTask =
            project.tasks.register(
                "mutationTest",
                MutationTask::class.java,
            ) { task ->
                task.group = "verification"
                task.description = "Runs mutation testing analysis"

                // Wire extension properties to task
                project.afterEvaluate {
                    task.targetClasses.from(extension.targetClasses)
                    task.testClasses.from(extension.testClasses)
                    task.classpath.from(extension.classpath)
                    task.coverageExecFile.set(extension.coverageExecFile)
                    task.enabledOperators.set(extension.enabledOperators)
                    task.timeoutMs.set(extension.timeoutMs)
                    task.maxParallelMutants.set(extension.maxParallelMutants)
                    task.reportFormat.set(extension.reportFormat)
                    task.outputDir.set(extension.outputDir)
                    task.failOnSurvived.set(extension.failOnSurvived)
                    task.excludedClasses.set(extension.excludedClasses)
                    task.excludedMethods.set(extension.excludedMethods)
                }
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

    private fun autoDetectJaCoCo(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        if (!extension.autoRunJaCoCo.get()) return

        try {
            val jacocoPlugin = project.plugins.findPlugin("jacoco")
            if (jacocoPlugin != null) {
                // JaCoCo is applied - look for .exec file
                val execFile = project.layout.buildDirectory.file("jacoco/test.exec")
                extension.coverageExecFile.set(execFile)
                project.logger.info("Auto-detected JaCoCo coverage file")
            }
        } catch (e: Exception) {
            project.logger.info("JaCoCo not available, skipping coverage analysis")
        }
    }

    private fun autoDetectClasspath(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            // Auto-detect testRuntimeClasspath
            val testRuntimeClasspath = project.configurations.findByName("testRuntimeClasspath")
            if (testRuntimeClasspath != null) {
                extension.classpath.from(testRuntimeClasspath)
                project.logger.info("Auto-detected classpath from testRuntimeClasspath")
            }

            // Also add main output to classpath
            val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
            val mainSourceSet = sourceSets?.findByName("main")
            if (mainSourceSet != null) {
                extension.classpath.from(mainSourceSet.output)
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect classpath: ${e.message}")
        }
    }
}
