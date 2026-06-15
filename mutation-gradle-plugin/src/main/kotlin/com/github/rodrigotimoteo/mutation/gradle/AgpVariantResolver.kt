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

        // AGP's `variant.runtimeConfiguration` exposes the app's runtime
        // classpath, NOT the unit-test runtime classpath. For mutation
        // testing we need testImplementation deps (JUnit, MockK, Mockito,
        // Robolectric) on the classpath or scanning the test classes for
        // mutation kill-checks will ClassNotFound. Merge the conventional
        // `debugUnitTestRuntimeClasspath` (and its release twin) into the
        // resolved classpath so MockK / Mockito / Robolectric are visible
        // to the mutation classloader.
        val cap = capture.name.replaceFirstChar { it.uppercaseChar() }
        listOf("${capture.name}UnitTestRuntimeClasspath", "${cap}UnitTestRuntimeClasspath")
            .distinct()
            .forEach { configName ->
                val cfg = project.configurations.findByName(configName)
                if (cfg != null) {
                    runtimeClasspath.from(cfg)
                    project.logger.info("Adding unit-test runtime classpath: $configName")
                }
            }

        val (mainClassesDir, testClassesDir) = findVariantClassesDirs(project, capture)
        val androidJar = locateAndroidJar(project, project.logger)

        return AndroidMutationContext(
            variantName = capture.name,
            runtimeClasspath = runtimeClasspath,
            classesDirs = classesDirs,
            testClassesDirs = testClassesDirs,
            mainClassesDir = mainClassesDir,
            testClassesDir = testClassesDir,
            androidJar = androidJar,
            compileTask = capture.compileTask,
            testCompileTask = capture.testCompileTask,
        )
    }

    /**
     * Resolve the primary main + unit-test classes directories for a
     * captured AGP variant. Looks up the conventional Kotlin compile task
     * (`compile<Variant>Kotlin`) first, then falls back to the Java/Javac
     * task (`compile<Variant>JavaWithJavac`), and returns the task's
     * `destinationDirectory` as a concrete [File].
     *
     * The fallback handles Android variants that build with pure Java
     * sources (no Kotlin plugin) and KMP Android targets whose variant
     * task name follows the Javac convention. Returns `null` for any
     * element whose compile task is absent — callers must handle the
     * nullable contract.
     */
    internal fun findVariantClassesDirs(
        project: Project,
        capture: VariantCapture,
    ): Pair<File?, File?> {
        val cap = capture.name.replaceFirstChar { it.uppercaseChar() }
        val mainDir =
            findJavaCompileDestination(project, "compile${cap}Kotlin")
                ?: findJavaCompileDestination(project, "compile${cap}JavaWithJavac")
        val testDir =
            findJavaCompileDestination(project, "compile${cap}UnitTestKotlin")
                ?: findJavaCompileDestination(project, "compile${cap}UnitTestJavaWithJavac")
        return mainDir to testDir
    }

    private fun findJavaCompileDestination(
        project: Project,
        taskName: String,
    ): File? {
        val task = project.tasks.findByName(taskName) ?: return null
        // Both JavaCompile (Javac) and KotlinCompile (Kotlin Gradle plugin)
        // expose a `destinationDirectory` property. Prefer the typed cast
        // for JavaCompile; for Kotlin tasks (incl. AGP variants whose
        // `compileDebugKotlin` is a KotlinCompile, not a JavaCompile),
        // fall back to reading the property by name so we resolve the
        // conventional Android output (e.g. `build/tmp/kotlin-classes/debug`).
        val javaCompile = task as? org.gradle.api.tasks.compile.JavaCompile
        if (javaCompile != null) {
            return javaCompile.destinationDirectory.asFile.orNull
        }
        if (task.hasProperty("destinationDirectory")) {
            val value = task.property("destinationDirectory")
            if (value is org.gradle.api.file.Directory) {
                return value.asFile
            }
            if (value is org.gradle.api.provider.Provider<*>) {
                @Suppress("UNCHECKED_CAST")
                val resolved = value.orNull as? org.gradle.api.file.Directory
                return resolved?.asFile
            }
            if (value is java.io.File) {
                return value
            }
        }
        return null
    }

    private fun locateAndroidJar(
        project: Project,
        logger: org.gradle.api.logging.Logger,
    ): File? {
        val android =
            try {
                project.extensions.findByType(BaseExtension::class.java)
            } catch (_: NoClassDefFoundError) {
                null
            }
        val compileSdk: String? = android?.compileSdkVersion
        val targetSdk: Int? = android?.defaultConfig?.targetSdkVersion?.apiLevel
        val jar = AndroidJarLocator().find(project, compileSdk, targetSdk)
        if (jar == null) {
            logger.warn(
                "android.jar not found in SDK. " +
                    "Set ANDROID_HOME or add android.jar manually via " +
                    "mutationTest { classpath.setFrom(...) }. " +
                    "Falling back to no android.jar — " +
                    "Android framework classes will fail to load.",
            )
        } else {
            logger.info("Auto-detected android.jar: $jar")
        }
        return jar
    }
}
