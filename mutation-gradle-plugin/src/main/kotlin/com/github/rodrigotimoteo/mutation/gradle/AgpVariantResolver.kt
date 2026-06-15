package com.github.rodrigotimoteo.mutation.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import java.io.File

/**
 * Resolves an [AndroidMutationContext] for a given AGP variant.
 *
 * The resolver is split into two phases to satisfy AGP's contract that
 * [AndroidComponentsExtension.onVariants] must be registered during the
 * `androidComponents { ... }` DSL block — never from `afterEvaluate`:
 *
 * 1. [registerOnVariants] — call from `MutationPlugin.apply()` (or from
 *    inside `project.plugins.withType(AppPlugin::class.java) {}`) to
 *    capture raw [VariantCapture]s for every Android variant. Must run
 *    before the AGP DSL block ends.
 * 2. [buildContext] — call from `afterEvaluate` (or any post-DSL phase)
 *    to convert a captured [VariantCapture] into a fully resolved
 *    [AndroidMutationContext] with live file collections and the
 *    matching `android.jar` for the variant's compileSdk.
 *
 * Pure-JVM projects (no AGP on the classpath) are supported: both methods
 * are safe no-ops and the plugin falls back to standard JVM detection.
 */
class AgpVariantResolver(private val objectFactory: ObjectFactory) {
    /**
     * Raw info captured during the AGP `onVariants` callback. Holds the
     * variant's [FileCollection] references (which are safe to query
     * later) and the conventional compile-task names so [buildContext]
     * does not need to re-enter the AGP API.
     *
     * @property name Variant name as reported by AGP (e.g. "debug").
     * @property runtimeConfiguration Lazy [FileCollection] for the
     *   variant's runtime classpath. Captured from the variant API at
     *   registration time; resolved at execution time.
     * @property compileTask Name of the Kotlin compile task for this
     *   variant (e.g. `compileDebugKotlin`).
     * @property testCompileTask Name of the Kotlin unit-test compile
     *   task (e.g. `compileDebugUnitTestKotlin`).
     */
    data class VariantCapture(
        val name: String,
        val runtimeConfiguration: FileCollection,
        val compileTask: String,
        val testCompileTask: String,
    )

    /**
     * Register `onVariants` callbacks on the project's
     * [AndroidComponentsExtension]. Must be called synchronously while
     * the AGP DSL block is still open — typically from inside
     * `project.plugins.withType(AppPlugin::class.java) {}` in the
     * plugin's `apply()` method.
     *
     * Populates [captures] with one [VariantCapture] per Android variant.
     * Safe to call when AGP is absent on the classpath (no-op).
     */
    fun registerOnVariants(
        project: Project,
        captures: MutableList<VariantCapture>,
    ) {
        try {
            val androidComponents =
                project.extensions.findByType(AndroidComponentsExtension::class.java)
                    ?: return
            androidComponents.onVariants { variant ->
                val cap = variant.name.replaceFirstChar { it.uppercaseChar() }
                captures.add(
                    VariantCapture(
                        name = variant.name,
                        runtimeConfiguration = variant.runtimeConfiguration,
                        compileTask = "compile${cap}Kotlin",
                        testCompileTask = "compile${cap}UnitTestKotlin",
                    ),
                )
            }
        } catch (_: NoClassDefFoundError) {
            // AGP not on classpath — pure-JVM project, no variant capture.
        }
    }

    /**
     * Find the captured variant matching [requestedName] (case-insensitive).
     * Returns `null` when no match is found.
     */
    fun findVariant(
        captures: List<VariantCapture>,
        requestedName: String,
    ): VariantCapture? = captures.firstOrNull { it.name.equals(requestedName, ignoreCase = true) }

    /**
     * Build an [AndroidMutationContext] for the given captured variant.
     * Resolves the live `runtimeClasspath`, the variant's main + test
     * classes directories, and the matching `android.jar` for the
     * variant's compileSdk. Safe to call from `afterEvaluate` or any
     * post-DSL phase.
     */
    fun buildContext(
        project: Project,
        capture: VariantCapture,
    ): AndroidMutationContext {
        val runtimeClasspath = objectFactory.fileCollection().from(capture.runtimeConfiguration)

        val classesDirs = objectFactory.fileCollection()
        val mainCompile = project.tasks.findByName(capture.compileTask)
        if (mainCompile != null) {
            classesDirs.from(mainCompile.outputs.files)
        }

        val testClassesDirs = objectFactory.fileCollection()
        val testCompile = project.tasks.findByName(capture.testCompileTask)
        if (testCompile != null) {
            testClassesDirs.from(testCompile.outputs.files)
        }

        val androidJar = project.findAndroidJar()

        return AndroidMutationContext(
            variantName = capture.name,
            runtimeClasspath = runtimeClasspath,
            classesDirs = classesDirs,
            testClassesDirs = testClassesDirs,
            androidJar = androidJar,
            compileTask = capture.compileTask,
            testCompileTask = capture.testCompileTask,
        )
    }

    private fun Project.findAndroidJar(): File {
        val android = extensions.findByType(BaseExtension::class.java)
        if (android == null) {
            val sdkPath =
                System.getenv("ANDROID_HOME")
                    ?: System.getenv("ANDROID_SDK_ROOT")
            val sdkDir =
                if (sdkPath != null) {
                    File(sdkPath)
                } else {
                    File(System.getProperty("user.home"), "Library/Android/sdk")
                }
            return File(sdkDir, "platforms/android-34/android.jar")
        }
        val sdkDir: File = android.sdkDirectory
        val compileSdk: String? = android.compileSdkVersion
        return if (compileSdk != null) {
            File(sdkDir, "platforms/android-$compileSdk/android.jar")
        } else {
            File(sdkDir, "platforms/android-34/android.jar")
        }
    }
}
