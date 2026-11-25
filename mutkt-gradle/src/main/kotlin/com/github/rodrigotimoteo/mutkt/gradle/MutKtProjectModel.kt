package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test

/** Lazy views of the project's source sets and of the `test` task's classpath; they follow later changes. */
internal class MutKtProjectModel(
    val mainClasses: FileCollection,
    val mainSources: FileCollection,
    val testClasses: FileCollection,
    val testRuntimeClasspath: FileCollection,
    val mainCompileClasspath: FileCollection,
)

private const val MODEL_KEY = "mutkt.projectModel"

/** The model, or null while the java plugin is absent. */
internal fun Project.mutktModel(): MutKtProjectModel? =
    extensions.extraProperties.let { if (it.has(MODEL_KEY)) it.get(MODEL_KEY) as MutKtProjectModel else null }

internal fun Project.registerMutktModel() {
    val sourceSets = extensions.getByType(JavaPluginExtension::class.java).sourceSets
    val main = sourceSets.named("main")
    val test = sourceSets.named("test")
    val testTask = tasks.named("test", Test::class.java)
    extensions.extraProperties.set(
        MODEL_KEY,
        MutKtProjectModel(
            mainClasses = files(main.map { it.output.classesDirs }),
            mainSources = files(main.map { it.allSource.sourceDirectories - it.resources.sourceDirectories }),
            testClasses = files(test.map { it.output.classesDirs }),
            // The task's own classpath: builds may append entries to it that the source set never sees. Not
            // testTask.map { }: a mapped task provider would make mutkt depend on (and run) the test task.
            testRuntimeClasspath = files(providers.provider { testTask.get().classpath }),
            mainCompileClasspath = files(main.map { it.compileClasspath }),
        ),
    )
}
