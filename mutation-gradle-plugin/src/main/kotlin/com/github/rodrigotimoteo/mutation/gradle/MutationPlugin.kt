package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Gradle plugin for Kotlin Mutation Testing.
 * Adds `mutationTest` task that runs PITest-style mutation testing.
 *
 * Usage:
 * ```
 * plugins {
 *     id("com.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
 * }
 *
 * mutationTest {
 *     enabledOperators = listOf("CONDITIONALS_BOUNDARY", "NEGATE_CONDITIONALS")
 *     timeoutMs = 60000
 *     maxParallelMutants = 8
 * }
 * ```
 */
class MutationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register extension for DSL configuration
        val extension = project.extensions.create(
            "mutationTest",
            MutationPluginExtension::class.java
        )

        // Register mutation test task
        val mutationTask = project.tasks.register(
            "runMutationTests",
            MutationTask::class.java
        ) { task ->
            task.group = "verification"
            task.description = "Runs mutation testing analysis"

            // Configure from extension after project evaluation
            project.afterEvaluate {
                task.enabledOperators.set(extension.enabledOperators)
                task.timeoutMs.set(extension.timeoutMs)
                task.maxParallelMutants.set(extension.maxParallelMutants)
                task.reportFormat.set(extension.reportFormat)
                task.outputDir.set(extension.outputDir)
            }
        }

        // Make verify depend on mutation tests (optional)
        project.tasks.matching { it.name == "check" }.all {
            it.dependsOn(mutationTask)
        }
    }
}