package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

/**
 * Resolved Android variant context for mutation testing.
 *
 * Captures everything the [MutationTask] needs to run against an Android
 * variant without re-querying AGP at execution time. The [runtimeClasspath],
 * [classesDirs], and [testClassesDirs] are live [ConfigurableFileCollection]s
 * so the task can stay configuration-cache safe.
 *
 * @property variantName Android variant name (e.g. "debug", "release").
 * @property runtimeClasspath Files required to run the variant at runtime.
 * @property classesDirs Compiled main classes for the variant.
 * @property testClassesDirs Compiled unit-test classes for the variant.
 * @property androidJar Path to the `android.jar` for the variant's compileSdk.
 * @property compileTask Name of the Kotlin compile task for this variant
 *   (e.g. `compileDebugKotlin`).
 * @property testCompileTask Name of the Kotlin unit-test compile task for
 *   this variant (e.g. `compileDebugUnitTestKotlin`).
 */
data class AndroidMutationContext(
    val variantName: String,
    val runtimeClasspath: ConfigurableFileCollection,
    val classesDirs: ConfigurableFileCollection,
    val testClassesDirs: ConfigurableFileCollection,
    val androidJar: File,
    val compileTask: String,
    val testCompileTask: String,
)
