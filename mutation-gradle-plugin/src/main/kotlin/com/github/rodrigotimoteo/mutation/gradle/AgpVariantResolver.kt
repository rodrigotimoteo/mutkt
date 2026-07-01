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
     * Slim by design: every field has to be either populated by AGP
     * (e.g. `runtimeConfiguration`, `compileTask`) or trivially
     * resolvable from the variant name. Adding state here has a real
     * cost — it forces every code path that builds a `VariantCapture`
     * to thread the new value through. The multi-flavor fix lives in
     * [buildContext] (where the ArtifactView is applied), not on
     * the capture itself.
     *
     * @property name Variant name as reported by AGP (e.g. "debug").
     * @property runtimeConfiguration Lazy [FileCollection] for the
     *   variant's runtime classpath. Captured from the variant API at
     *   registration time; resolved at execution time. Used as a
     *   fallback in [buildContext] when the configuration-name lookup
     *   fails (e.g. older AGP without the per-variant
     *   `<variant>RuntimeClasspath` configuration).
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
        // The main runtime classpath goes through the same
        // `artifactType=jar` ArtifactView treatment as the unit
        // test classpath below. AGP's `variant.runtimeConfiguration`
        // (e.g. `debugRuntimeClasspath`) is created without
        // `artifactType=jar` when the consumer has no own product
        // flavors — even though it carries the right
        // `BuildTypeAttr` / `ProductFlavor:*` attributes from the
        // `missingDimensionStrategy`. The library's
        // `*RuntimeElements` configuration publishes multiple
        // `artifactType` sub-variants and the consumer needs to
        // declare which one it wants. Without the filter, Gradle
        // raises `Cannot choose between the following variants`.
        val mainRuntimeCfg = findMainRuntimeConfiguration(project, capture.name)
        val runtimeClasspath =
            if (mainRuntimeCfg != null) {
                val jarFiltered = mainRuntimeArtifactView(mainRuntimeCfg)
                objectFactory.fileCollection().from(jarFiltered).also {
                    project.logger.info(
                        "Using artifactType=jar filtered view of '${mainRuntimeCfg.name}' " +
                            "for variant '${capture.name}'",
                    )
                }
            } else {
                objectFactory.fileCollection().from(capture.runtimeConfiguration)
            }

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

        // Multi-flavor library failure mode (unit test classpath): when
        // the app has no own product flavors but depends on a
        // library that publishes multiple runtime variants
        // (e.g. `productionDebugRuntimeElements` and
        // `stagingDebugRuntimeElements` on a `brand` dimension),
        // the app's `debugUnitTestRuntimeClasspath` is set up with
        // `BuildTypeAttr=debug` + `ProductFlavor:brand=production`
        // (via the app's `defaultConfig.missingDimensionStrategy`)
        // but is MISSING `artifactType=jar`. Same fix as the main
        // runtime classpath above: wrap the configuration in an
        // `ArtifactView` that requires `artifactType=jar`.
        val unitTestCfg = findUnitTestRuntimeConfiguration(project, capture.name)
        if (unitTestCfg != null) {
            val jarFiltered = unitTestArtifactView(unitTestCfg)
            runtimeClasspath.from(jarFiltered)
            project.logger.info(
                "Using artifactType=jar filtered view of '${unitTestCfg.name}' " +
                    "for variant '${capture.name}'",
            )
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
     * Locate the unit-test runtime classpath [Configuration] for the
     * captured variant. AGP creates a per-variant configuration with
     * the conventional name `<variant>UnitTestRuntimeClasspath`
     * (e.g. `productionDebugUnitTestRuntimeClasspath`); the resolver
     * also probes the capitalized form for AGP quirk tolerance.
     *
     * Returns `null` when no matching configuration exists. The
     * caller falls back to a heuristic or skips the test classpath
     * contribution in that case.
     */
    private fun findUnitTestRuntimeConfiguration(
        project: Project,
        variantName: String,
    ): org.gradle.api.artifacts.Configuration? {
        val cap = variantName.replaceFirstChar { it.uppercaseChar() }
        val candidates = listOf("${variantName}UnitTestRuntimeClasspath", "${cap}UnitTestRuntimeClasspath")
        for (name in candidates) {
            val cfg = project.configurations.findByName(name)
            if (cfg != null) return cfg
        }
        return null
    }

    /**
     * Locate the main runtime classpath [Configuration] for the
     * captured variant. AGP creates a per-variant configuration
     * with the conventional name `<variant>RuntimeClasspath`
     * (e.g. `productionDebugRuntimeClasspath`); the resolver
     * also probes the capitalized form for AGP quirk tolerance.
     *
     * Returns `null` when no matching configuration exists. The
     * caller falls back to the captured `runtimeConfiguration`
     * (a `FileCollection` from `variant.runtimeConfiguration`).
     */
    private fun findMainRuntimeConfiguration(
        project: Project,
        variantName: String,
    ): org.gradle.api.artifacts.Configuration? {
        val cap = variantName.replaceFirstChar { it.uppercaseChar() }
        val candidates = listOf("${variantName}RuntimeClasspath", "${cap}RuntimeClasspath")
        for (name in candidates) {
            val cfg = project.configurations.findByName(name)
            if (cfg != null) return cfg
        }
        return null
    }

    /**
     * Build an `artifactType=jar` `ArtifactView` of the main
     * runtime configuration. See [unitTestArtifactView] for the
     * rationale — both classpaths need the filter to keep
     * flavored project dependencies resolvable.
     */
    private fun mainRuntimeArtifactView(cfg: org.gradle.api.artifacts.Configuration): org.gradle.api.file.FileCollection =
        artifactViewWithJar(cfg)

    private fun unitTestArtifactView(cfg: org.gradle.api.artifacts.Configuration): org.gradle.api.file.FileCollection =
        artifactViewWithJar(cfg)

    /**
     * Wrap [cfg] in an `ArtifactView` that requires
     * `artifactType=jar`. Shared by the main + unit test
     * runtime classpath lookups. See [unitTestArtifactView]'s
     * kdoc for the rationale on why the filter is required
     * for the multi-flavor library variant ambiguity failure.
     */
    private fun artifactViewWithJar(cfg: org.gradle.api.artifacts.Configuration): org.gradle.api.file.FileCollection {
        // Typed Kotlin call: `incoming.artifactView { ... }` is
        // stable across Gradle 5.x — 8.x and the
        // `AttributeContainer.attribute(Attribute<T>, T)` method
        // on the ViewConfiguration's attributes is on
        // `gradle-core-api`. No reflection needed: the
        // `ArtifactView$ViewConfiguration` and
        // `AttributeContainer` types are both in the public
        // Gradle API and the plugin already declares
        // `implementation(gradleApi())` in its build script.
        val view =
            cfg.incoming.artifactView {
                it.attributes.attribute(
                    org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                    "jar",
                )
            }
        return view.files
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

    /**
     * Build a human-readable error message for a failed Android
     * variant resolution. The message names the exact unmatched
     * attribute and the consumer configuration that needed it, so
     * users get a one-line fix (`missingDimensionStrategy(...)`)
     * instead of a generic "cannot choose between variants" stack.
     *
     * Format:
     * ```
     * Could not resolve Android variant '<variant>' for project '<project>':
     *   The consumer's <config> configuration is missing attribute
     *   '<attribute>=<value>' that the producer '<producer>' requires.
     * Fix: add `missingDimensionStrategy("<dimension>", "<value>")` to the
     * consumer's `android.defaultConfig { }` block (or wire
     * `mutationTest.classpath` manually).
     * ```
     *
     * Returns `null` when the exception does not match the AGP
     * variant-resolution error pattern; callers fall back to the
     * raw exception in that case.
     */
    @Suppress("ReturnCount")
    fun formatVariantResolutionError(
        throwable: Throwable,
        capture: VariantCapture,
        project: Project,
    ): String? {
        val message = throwable.message ?: return null
        // Gradle's "Cannot choose between the following variants" /
        // "Could not resolve" errors mention attributes in two
        // shapes: `<name>=<value>` (constraint failure) and
        // `<name>='<value>'` (the consumer / producer attribute
        // listing). We accept both so the same formatter handles
        // AGP 7.x and 8.x error formats. The first matching
        // `com.android.build.api.attributes.ProductFlavor:*` entry
        // wins because that is the dimension the consumer is
        // missing — `BuildTypeAttr` is usually present.
        val attrMatch =
            Regex(
                """(com\.android\.build\.api\.attributes\.[A-Za-z]+):([A-Za-z][A-Za-z0-9_\-]*)=['"]?([^'\s,"]+)['"]?""",
            ).find(message)
        val attr = attrMatch?.value
        val dim = attrMatch?.groupValues?.getOrNull(2)
        val value = attrMatch?.groupValues?.getOrNull(3)
        val producer = guessProducerProject(message, project)
        val sb = StringBuilder()
        sb.append("Could not resolve Android variant '").append(capture.name)
            .append("' for project '").append(project.path).append("'.\n")
        if (attr != null) {
            sb.append("  The consumer's runtime classpath is missing attribute\n")
            sb.append("  '").append(attr).append("' that the producer")
            if (producer != null) sb.append(" '").append(producer).append("'")
            sb.append(" requires.\n")
        } else {
            sb.append("  Variant resolution failed: ").append(message.lineSequence().first()).append("\n")
        }
        if (dim != null && value != null) {
            sb.append("Fix: add `missingDimensionStrategy(\"").append(dim)
                .append("\", \"").append(value).append("\")` to the consumer's\n")
            sb.append("`android.defaultConfig { }` block in `").append(project.buildFile.name)
                .append("`, or wire `mutationTest.classpath` manually\n")
            sb.append("with the producer's `<variant>RuntimeElements` configuration.\n")
        } else {
            sb.append("Fix: ensure the consumer's `defaultConfig` declares a\n")
            sb.append("`missingDimensionStrategy` for every flavor dimension the\n")
            sb.append("producer publishes, or restrict `mutationTest.androidVariant`\n")
            sb.append("to a variant the producer exposes.\n")
        }
        return sb.toString()
    }

    /**
     * Best-effort extraction of the producer project path from a
     * Gradle variant-resolution error message. Returns `null` when
     * no `project ':...'` token is present.
     */
    private fun guessProducerProject(
        message: String,
        @Suppress("UNUSED_PARAMETER") project: Project,
    ): String? = Regex("""project '(:[^']+)'""").find(message)?.groupValues?.getOrNull(1)
}
