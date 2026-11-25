package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import java.io.File

/** Lazy views of the project's source sets and of the `test` task's classpath; they follow later changes. */
internal class MutKtProjectModel(
    val mainClasses: FileCollection,
    val mainSources: FileCollection,
    val testClasses: FileCollection,
    val supportClasses: FileCollection,
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
    val testTask = tasks.named("test", Test::class.java)
    extensions.extraProperties.set(
        MODEL_KEY,
        MutKtProjectModel(
            mainClasses = files(main.map { it.output.classesDirs }),
            mainSources = files(main.map { it.allSource.sourceDirectories - it.resources.sourceDirectories }),
            testClasses = files(test.map { it.output.classesDirs }),
            supportClasses = files(providers.provider { supportClasses(sourceSets, testTask.get().classpath) }),
            // The task's own classpath: builds may append entries to it that the source set never sees. Not
            // testTask.map { }: a mapped task provider would make mutkt depend on (and run) the test task.
            testRuntimeClasspath = files(providers.provider { testTask.get().classpath }),
        ),
    )
}

// Classes of the other source sets the tests load (java-test-fixtures, shared test code), as a directory or as the
// source set's jar. Resolving the classpath only names its files; it builds nothing.
private fun Project.supportClasses(
    sourceSets: SourceSetContainer,
    classpath: FileCollection,
): FileCollection {
    val entries = classpath.files
    val loaded =
        sourceSets
            .filter { it.name != SourceSet.MAIN_SOURCE_SET_NAME && it.name != SourceSet.TEST_SOURCE_SET_NAME }
            .filter { set -> set.output.classesDirs.any { it in entries } || jarOf(set) in entries }
    return files(loaded.map { it.output.classesDirs })
}

// A source set's jar (testFixturesJar, for instance) packs its classesDirs, so those directories stand in for it.
private fun Project.jarOf(set: SourceSet): File? =
    tasks
        .withType(Jar::class.java)
        .findByName(set.jarTaskName)
        ?.archiveFile
        ?.get()
        ?.asFile
