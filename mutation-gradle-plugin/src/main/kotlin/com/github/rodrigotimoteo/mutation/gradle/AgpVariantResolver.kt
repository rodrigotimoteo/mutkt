package com.github.rodrigotimoteo.mutation.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
     * Memoize the KMP-Android probe per [Project]. `onVariants` is
     * called once per Android variant (typically debug + release), so
     * without this cache the reflective `hasPlugin` lookup runs on
     * every variant even though the result is invariant for the
     * lifetime of the project. ConcurrentHashMap is overkill for a
     * single build but keeps the resolver safe if a future caller
     * invokes it from parallel configuration blocks.
     */
    private val kmpAndroidCache = ConcurrentHashMap<Project, Boolean>()

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
        // Idempotency guard. AGP's `onVariants` is additive: each call
        // appends a new callback. The plugin wires five Android plugin
        // types (App, Library, DynamicFeature, Test, InstantApp) and a
        // Gradle project can normally only have one of them applied,
        // but a defensive guard keeps the resolver safe against future
        // plugin combos or accidental re-application. The flag lives on
        // the project's extension container so it survives across the
        // multiple `withType` callbacks in MutationPlugin.
        val registeredKey = "mutkt.androidVariantsRegistered"
        val extras = project.extensions.extraProperties
        if (extras.has(registeredKey)) return
        extras.set(registeredKey, true)
        try {
            val androidComponents =
                project.extensions.findByType(AndroidComponentsExtension::class.java)
                    ?: return
            androidComponents.onVariants { variant ->
                val cap = variant.name.replaceFirstChar { it.uppercaseChar() }
                // KMP Android targets publish Kotlin compile tasks with an
                // `Android` suffix (e.g. `compileDebugKotlinAndroid` /
                // `compileDebugUnitTestKotlinAndroid`). Standard AGP
                // produces `compileDebugKotlin` / `compileDebugUnitTestKotlin`.
                // Detect the suffix at capture time so later lookups in
                // `findByName(...)` match the task that actually exists.
                val isKmp = isKmpAndroidTarget(project)
                val compileSuffix = if (isKmp) "KotlinAndroid" else "Kotlin"
                val unitTestSuffix = if (isKmp) "UnitTestKotlinAndroid" else "UnitTestKotlin"
                captures.add(
                    VariantCapture(
                        name = variant.name,
                        runtimeConfiguration = variant.runtimeConfiguration,
                        compileTask = "compile${cap}$compileSuffix",
                        testCompileTask = "compile${cap}$unitTestSuffix",
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
        // Prefer the task name we captured (handles the KMP Android
        // `KotlinAndroid` suffix), then fall back to the standard
        // `compile<Variant>Kotlin` and the Javac fallback for plain-Java
        // Android variants.
        val mainDir =
            findJavaCompileDestination(project, capture.compileTask)
                ?: findJavaCompileDestination(project, "compile${cap}Kotlin")
                ?: findJavaCompileDestination(project, "compile${cap}JavaWithJavac")
        val testDir =
            findJavaCompileDestination(project, capture.testCompileTask)
                ?: findJavaCompileDestination(project, "compile${cap}UnitTestKotlin")
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
        // AGP 8.x deprecates BaseExtension.compileSdkVersion and
        // defaultConfig.targetSdkVersion.apiLevel. Prefer the modern
        // CommonExtension.compileSdk (Int) and targetSdk (Int) accessors
        // when the AGP version exposes them; fall back to the legacy
        // BaseExtension path for AGP 7.x. Lookups go through reflection
        // so the plugin stays compatible with AGP variants resolved via
        // `compileOnly` dependency declarations (no compile-time link
        // against CommonExtension).
        val compileSdk: String? = resolveCompileSdk(android)
        val targetSdk: Int? = resolveTargetSdk(android)
        // Robolectric-targeted Android projects pin a specific SDK
        // level in `robolectric.properties`. Probe that file before
        // falling back to compileSdk / targetSdk so Robolectric tests
        // run against the matching android.jar.
        val robolectricSdk = AndroidJarLocator().findRobolectricSdk(project)
        val effectiveSdk = robolectricSdk ?: compileSdk
        val jar = AndroidJarLocator().find(project, effectiveSdk, targetSdk)
        if (jar == null) {
            // Hard error for Android tasks. A missing android.jar causes
            // a `ClassNotFoundException` deep inside the mutant
            // classloader (the test runtime resolves `android.app.Activity`
            // and friends) which is much harder to diagnose than a clear
            // upfront failure. Users who genuinely want to skip the
            // android.jar can opt out with `mutationTest {
            // androidJar.set(null) }` in their build script.
            throw org.gradle.api.GradleException(
                "android.jar not found in SDK (looked for SDK " +
                    "${effectiveSdk ?: "default"}). " +
                    "Set ANDROID_HOME / ANDROID_SDK_ROOT, configure " +
                    "`local.properties`'s `sdk.dir`, or wire the jar " +
                    "manually with `mutationTest { androidJar.set(file(...)) }`. " +
                    "To explicitly skip android.jar resolution, use " +
                    "`mutationTest { androidJar.set(null) }`.",
            )
        }
        logger.info("Auto-detected android.jar: $jar")
        return jar
    }

    /**
     * Resolve the compileSdk as a String, preferring the modern
     * [com.android.build.api.dsl.CommonExtension.compileSdk] (Int) over
     * the deprecated [com.android.build.gradle.BaseExtension.compileSdkVersion]
     * (String). Returns `null` when neither accessor is reachable.
     */
    private fun resolveCompileSdk(android: Any?): String? {
        if (android == null) return null
        val modern: Int? = readIntProperty(android, "getCompileSdk")
        if (modern != null) return modern.toString()
        return try {
            android.javaClass.getMethod("getCompileSdkVersion").invoke(android)?.toString()
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolve the targetSdk as an Int, preferring the modern
     * [com.android.build.api.dsl.CommonExtension.targetSdk] (Int) over the
     * deprecated `defaultConfig.targetSdkVersion.apiLevel` path. Returns
     * `null` when neither accessor is reachable.
     */
    private fun resolveTargetSdk(android: Any?): Int? {
        if (android == null) return null
        val modern: Int? = readIntProperty(android, "getTargetSdk")
        if (modern != null) return modern
        return try {
            val defaultConfig =
                android.javaClass.getMethod("getDefaultConfig").invoke(android)
                    ?: return null
            val targetSdkVersion =
                defaultConfig.javaClass.getMethod("getTargetSdkVersion").invoke(defaultConfig)
                    ?: return null
            targetSdkVersion.javaClass.getMethod("getApiLevel").invoke(targetSdkVersion) as? Int
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read an `Int` (or `Provider<Int>`) property by getter name via
     * reflection. Returns `null` when the getter is absent or the value
     * is not a usable Int — used to probe for AGP version-specific
     * accessors without a compile-time dependency on
     * `CommonExtension`.
     */
    private fun readIntProperty(
        target: Any,
        getterName: String,
    ): Int? {
        val value =
            try {
                target.javaClass.getMethod(getterName).invoke(target)
            } catch (_: NoSuchMethodException) {
                return null
            } catch (_: Exception) {
                return null
            }
        return when (value) {
            is Int -> value
            is org.gradle.api.provider.Provider<*> -> value.orNull as? Int
            else -> null
        }
    }

    /**
     * Detect whether [project] is a Kotlin Multiplatform Android target.
     *
     * KMP Android targets publish Kotlin compile tasks with an
     * `Android` suffix (`compileDebugKotlinAndroid`) which the standard
     * AGP Android plugin does not produce. Heuristic: a project applies
     * the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`)
     * and the AGP plugin. Probed reflectively so this code links when
     * KGP is absent (pure-JVM / pure-Android builds). The Kotlin Gradle
     * plugin is a `compileOnly` dependency of the mutation plugin.
     */
    private fun isKmpAndroidTarget(project: Project): Boolean =
        kmpAndroidCache.getOrPut(project) {
            try {
                project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            } catch (_: Exception) {
                false
            }
        }
}
