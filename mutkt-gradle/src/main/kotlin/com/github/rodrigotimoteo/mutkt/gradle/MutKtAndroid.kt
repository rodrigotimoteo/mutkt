package com.github.rodrigotimoteo.mutkt.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Registers `mutkt` for the variant the `variant` DSL names, reading the classes from AGP and the tests from the
 * variant's unit-test task; fails the build, once AGP created its variants, unless that variant has unit tests.
 */
internal fun Project.wireAndroid(extension: MutKtExtension) {
    // A value rather than a convention, so add() extends the defaults and set() replaces them.
    extension.excludedClasses.addAll(ANDROID_GENERATED_CLASSES)
    val components = extensions.getByType(AndroidComponentsExtension::class.java)
    // Variant name to whether it has a unit-test component.
    val variants = mutableMapOf<String, Boolean>()
    components.onVariants(components.selector().all()) { variant: Variant ->
        val unitTest = (variant as? HasUnitTest)?.unitTest
        variants[variant.name] = unitTest != null
        if (unitTest != null && variant.name == extension.variant.get()) {
            registerAndroidMutkt(extension, variant, unitTest)
        }
    }
    // Registered after AGP's own afterEvaluate, which runs the onVariants callbacks.
    afterEvaluate {
        val wanted = extension.variant.get()
        val problem =
            when (variants[wanted]) {
                null -> "MutKt cannot find variant '$wanted'"
                false -> "MutKt variant '$wanted' has no unit tests"
                true -> return@afterEvaluate
            }
        val choices = variants.filterValues { hasUnitTest -> hasUnitTest }.keys.sorted()
        throw GradleException("$problem; variants with unit tests: ${choices.joinToString().ifEmpty { "none" }}")
    }
}

private fun Project.registerAndroidMutkt(
    extension: MutKtExtension,
    variant: Variant,
    unitTest: UnitTest,
) {
    // AGP's name for the unit-test task (computeTaskName("test") on the unit-test component): a convention, not API.
    val testTask = "test${variant.name.replaceFirstChar(Char::uppercaseChar)}UnitTest"
    val test = providers.provider { unitTestTask(testTask, variant.name) }
    val sources = listOfNotNull(variant.sources.java?.all, variant.sources.kotlin?.all)
    val mutkt =
        registerMutkt(MutKtAndroidTask::class.java, extension, test) { task ->
            task.classesDirs.from(task.classJars, task.classDirectories)
            task.testClassesDirs.from(test.map { it.testClassesDirs })
            task.testRuntimeClasspath.from(
                test.map { it.classpath },
                missingPlatformLauncher(providers.provider { unitTest.runtimeConfiguration }, extension.engines),
            )
            task.compileClasspath.from(variant.compileClasspath)
            task.sourceDirs.from(sources)
        }
    variant.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(mutkt)
        .toGet(ScopedArtifact.CLASSES, MutKtAndroidTask::classJars, MutKtAndroidTask::classDirectories)
}

private fun Project.unitTestTask(
    name: String,
    variant: String,
): Test {
    if (name in tasks.names) return tasks.named(name, Test::class.java).get()
    throw GradleException("MutKt cannot find task '$name' for the unit tests of variant '$variant'")
}
