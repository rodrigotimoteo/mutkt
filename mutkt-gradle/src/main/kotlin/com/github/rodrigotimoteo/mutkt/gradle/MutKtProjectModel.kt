package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension

/** Lazy views of the project's main and test source sets; they follow later source set changes. */
internal class MutKtProjectModel(
    val mainClasses: FileCollection,
    val mainSources: FileCollection,
    val testClasses: FileCollection,
    val testRuntimeClasspath: FileCollection,
)

private const val MODEL_KEY = "mutkt.projectModel"

/** The model, or null while the java plugin is absent. */
internal fun Project.mutktModel(): MutKtProjectModel? =
    extensions.extraProperties.let { if (it.has(MODEL_KEY)) it.get(MODEL_KEY) as MutKtProjectModel else null }

internal fun Project.registerMutktModel() {
    val sourceSets = extensions.getByType(JavaPluginExtension::class.java).sourceSets
    val main = sourceSets.named("main")
    val test = sourceSets.named("test")
    extensions.extraProperties.set(
        MODEL_KEY,
        MutKtProjectModel(
            mainClasses = files(main.map { it.output.classesDirs }),
            mainSources = files(main.map { it.allSource.sourceDirectories - it.resources.sourceDirectories }),
            testClasses = files(test.map { it.output.classesDirs }),
            testRuntimeClasspath = files(test.map { it.runtimeClasspath }),
        ),
    )
}
