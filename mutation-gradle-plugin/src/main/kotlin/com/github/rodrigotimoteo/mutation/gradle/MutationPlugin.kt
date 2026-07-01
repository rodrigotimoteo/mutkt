package com.github.rodrigotimoteo.mutation.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Gradle plugin for Kotlin Mutation Testing.
 *
 * Adds `mutationTest` task that runs PITest-style mutation testing.
 * Zero-config: just apply the plugin and run `./gradlew mutationTest`.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("io.github.rodrigotimoteo.mutation-kotlin") version "0.3.0"
 * }
 * ```
 *
 * Custom configuration:
 * ```kotlin
 * mutationTest {
 *     timeoutMs.set(60000)
 *     maxParallelMutants.set(8)
 *     reportFormats.set(setOf("html", "console"))
 *     failOnScoreThreshold.set(70)
 * }
 * ```
 */
class MutationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register extension for DSL configuration
        val extension =
            project.extensions.create(
                "mutationTest",
                MutationPluginExtension::class.java,
                project,
            )

        val agpVariantResolver = AgpVariantResolver(project.objects)
        val variantCaptures = mutableListOf<AgpVariantResolver.VariantCapture>()

        // Detect Android Gradle Plugin. withType callbacks only fire if AGP is on the
        // classpath, so this is a no-op for pure-JVM projects. The class literals would
        // fail to resolve at runtime when AGP is absent (test classpath, pure-JVM users),
        // so we guard the registration.
        try {
            project.plugins.withType(AppPlugin::class.java) {
                extension.isAndroid.set(true)
                extension.androidPluginType.set("application")
                // Must register onVariants synchronously while the AGP DSL block is
                // still open. Calling this from afterEvaluate yields the
                // "It is too late to add actions" error from AGP.
                agpVariantResolver.registerOnVariants(project, variantCaptures)
            }
            project.plugins.withType(LibraryPlugin::class.java) {
                extension.isAndroid.set(true)
                extension.androidPluginType.set("library")
                agpVariantResolver.registerOnVariants(project, variantCaptures)
            }
            // AGP also ships DynamicFeaturePlugin, TestPlugin, and
            // InstantAppPlugin. None of these add new task names we care
            // about (they all publish the standard compile<Variant>Kotlin
            // / compile<Variant>UnitTestKotlin tasks via their consumed
            // base plugin), but the project must be flagged as Android
            // so the variant resolver runs. Looked up reflectively because
            // these classes live in sub-jars of AGP that the plugin only
            // depends on transitively, and referencing them directly would
            // require a hard `implementation` dependency on AGP.
            registerAndroidVariantSubtype(
                project,
                "com.android.build.gradle.DynamicFeaturePlugin",
                "dynamicFeature",
                extension,
                agpVariantResolver,
                variantCaptures,
            )
            registerAndroidVariantSubtype(
                project,
                "com.android.build.gradle.TestPlugin",
                "test",
                extension,
                agpVariantResolver,
                variantCaptures,
            )
            registerAndroidVariantSubtype(
                project,
                "com.android.build.gradle.InstantAppPlugin",
                "instantApp",
                extension,
                agpVariantResolver,
                variantCaptures,
            )
        } catch (_: NoClassDefFoundError) {
            // AGP not on classpath — pure-JVM project, detection skipped.
        }

        // Register mutation test task with lazy property wiring
        val mutationTask =
            project.tasks.register(
                "mutationTest",
                MutationTask::class.java,
            ) { task ->
                task.group = "verification"
                task.description = "Runs mutation testing analysis"

                // Discover the Kotlin compile tasks for both pure-JVM and Android
                // projects. Android variants use the captured AGP variant task
                // names (e.g. compileDebugKotlin / compileDebugUnitTestKotlin)
                // when available — those are guaranteed to exist for the
                // project's actual variant set. Pure-JVM uses compileKotlin /
                // compileTestKotlin (or compileKotlinJvm for KMP). Hardcoded
                // fallbacks are avoided for Android to prevent depending on
                // tasks that may not exist in release-only or custom variant
                // configurations. withType is evaluated at task-realization
                // time so all plugins (including AGP) have been applied.
                val isAndroid = extension.isAndroid.getOrElse(false)
                // Compile-task dependency wiring for Android is deferred
                // entirely to afterEvaluate via resolveAndroidContext.
                // Pre-wiring here would pick the debug (or first) capture
                // and ignore extension.androidVariant, so a user requesting
                // the release variant would still get the debug compile
                // tasks on the dependency graph. Pure-JVM / KMP projects
                // are wired immediately since they have a stable task set
                // (compileKotlin / compileTestKotlin).
                if (!isAndroid) {
                    val (compileTaskName, testCompileTaskName) =
                        discoverKotlinCompileTasks(project, isAndroid, variantCaptures)
                    // Only wire dependencies for tasks that actually exist or are
                    // standard pure-JVM defaults. For Android with no AGP variant
                    // capture, the wiring is deferred to afterEvaluate where
                    // resolveAndroidContext re-wires with the correct names.
                    if (compileTaskName.isNotEmpty() && testCompileTaskName.isNotEmpty()) {
                        task.dependsOn(compileTaskName, testCompileTaskName)
                    }
                }

                // Wire extension properties lazily
                task.targetClasses.from(extension.targetClasses)
                task.testClasses.from(extension.testClasses)
                task.classpath.from(extension.classpath)
                task.enabledOperators.set(extension.enabledOperators)
                task.timeoutMs.set(extension.timeoutMs)
                task.maxParallelMutants.set(extension.maxParallelMutants)
                task.reportFormats.set(extension.reportFormats)
                task.failOnSurvived.set(extension.failOnSurvived)
                task.excludedClasses.set(extension.excludedClasses)
                task.excludedMethods.set(extension.excludedMethods)
                task.failOnScoreThreshold.set(extension.failOnScoreThreshold)
                task.coverageExecFile.set(extension.coverageExecFile)
                task.reportsDir.set(extension.outputDir)
                // Wire newly added extension properties
                task.failOnMutationScoreThreshold.set(extension.failOnMutationScoreThreshold)
                task.maxMutationsPerClass.set(extension.maxMutationsPerClass)
                task.enableIncrementalAnalysis.set(extension.enableIncrementalAnalysis)
                task.mutantTimeoutMs.set(extension.mutantTimeoutMs)
                task.targetClassPatterns.set(extension.targetClassPatterns)
                task.targetTestPatterns.set(extension.targetTestPatterns)
                task.excludeClassPatterns.set(extension.excludeClassPatterns)
                task.excludeTestPatterns.set(extension.excludeTestPatterns)
                task.enableSubsumption.set(extension.enableSubsumption)
                task.enableWeakMutation.set(extension.enableWeakMutation)
                task.enableInlinedFinally.set(extension.enableInlinedFinally)
                task.enableTestOrdering.set(extension.enableTestOrdering)
                task.showClassScores.set(extension.showClassScores)
                task.generateGraph.set(extension.generateGraph)
                task.enableCache.set(extension.enableCache)
                task.targetPackages.set(extension.targetPackages)
                task.excludePackages.set(extension.excludePackages)
                task.ciMode.set(extension.ciMode)
                task.verbose.set(extension.verbose)
                task.excludeGeneratedClasses.set(extension.excludeGeneratedClasses)
                task.androidContext.set(extension.androidContext)
            }

        // Auto-detect sourceSets after project evaluation
        project.afterEvaluate {
            autoDetectSourceSets(project, extension)
            autoDetectKmpSourceSets(project, extension)
            autoDetectClasspath(project, extension)
            autoDetectJaCoCo(project, extension)
            resolveAndroidContext(project, extension, mutationTask, agpVariantResolver, variantCaptures)
        }

        // Don't auto-depend on check - mutationTest is opt-in
        // Users run: ./gradlew mutationTest
    }

    private fun autoDetectSourceSets(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
            if (sourceSets == null) {
                project.logger.info("No SourceSetContainer found (KMP project), skipping auto-detect")
                return
            }

            // Auto-detect main source set output
            val mainSourceSet = sourceSets.findByName("main")
            if (mainSourceSet != null) {
                extension.targetClasses.from(mainSourceSet.output)
                project.logger.info("Auto-detected target classes from sourceSets.main.output")
            }

            // Auto-detect test source set output
            val testSourceSet = sourceSets.findByName("test")
            if (testSourceSet != null) {
                extension.testClasses.from(testSourceSet.output)
                project.logger.info("Auto-detected test classes from sourceSets.test.output")
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect sourceSets: ${e.message}")
        }
    }

    private fun autoDetectKmpSourceSets(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        // KMP projects expose source sets via KotlinProjectExtension
        // (e.g. jvmMain, commonMain, androidMain, iosMain). Pure-JVM
        // projects don't have it, so a missing class is a no-op. The
        // Kotlin Gradle Plugin is a compileOnly dependency of this
        // plugin, so we cannot reference the type directly — use
        // reflection to stay safe for users who apply this plugin to
        // a project that does not apply kotlin("multiplatform").
        try {
            val kotlinExtClass = Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension")
            val kotlinExt = project.extensions.findByType(kotlinExtClass) ?: return
            val sourceSetsContainer = kotlinExt.javaClass.getMethod("getSourceSets").invoke(kotlinExt) ?: return
            val sourceSets = sourceSetsContainer as? Iterable<*> ?: return

            var mainCount = 0
            var testCount = 0
            for (sourceSet in sourceSets) {
                val name = sourceSet?.javaClass?.getMethod("getName")?.invoke(sourceSet) as? String ?: continue
                // KMP convention: main-like source sets end with "Main"
                // (e.g. jvmMain, iosMain); test-like end with "Test"
                // (e.g. jvmTest, commonTest). Skip intermediates and
                // anything that does not match this convention.
                val isMainLike = name.endsWith("Main")
                val isTestLike = name.endsWith("Test")
                if (!isMainLike && !isTestLike) continue

                val output =
                    sourceSet?.javaClass
                        ?.getMethod("getOutput")
                        ?.invoke(sourceSet)
                        ?: continue
                val classesDirs =
                    output?.javaClass
                        ?.getMethod("getClassesDirs")
                        ?.invoke(output)
                        ?: continue
                val files = (classesDirs as org.gradle.api.file.FileCollection).files
                if (files.isEmpty()) continue
                if (isMainLike) {
                    extension.targetClasses.from(files)
                    mainCount++
                } else {
                    extension.testClasses.from(files)
                    testCount++
                }
            }
            if (mainCount > 0 || testCount > 0) {
                project.logger.info(
                    "Auto-detected KMP source sets: $mainCount main, $testCount test",
                )
            }
        } catch (_: ClassNotFoundException) {
            // Kotlin Gradle Plugin not on classpath — pure-JVM project, no-op.
        } catch (_: NoClassDefFoundError) {
            // Kotlin Gradle Plugin linkage failure — also a no-op.
        } catch (e: Exception) {
            project.logger.debug("KMP source set detection skipped: ${e.message}")
        }
    }

    private fun autoDetectClasspath(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            // Auto-detect testRuntimeClasspath (already includes main output)
            val testRuntimeClasspath = project.configurations.findByName("testRuntimeClasspath")
            if (testRuntimeClasspath != null) {
                extension.classpath.from(testRuntimeClasspath)
                project.logger.info("Auto-detected classpath from testRuntimeClasspath")
            } else {
                // KMP projects use jvmTestRuntimeClasspath
                project.configurations.findByName("jvmTestRuntimeClasspath")?.let {
                    extension.classpath.from(it)
                    project.logger.info("Auto-detected classpath from jvmTestRuntimeClasspath (KMP)")
                }
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect classpath: ${e.message}")
        }
    }

    private fun autoDetectJaCoCo(
        project: Project,
        extension: MutationPluginExtension,
    ) {
        try {
            // Check if JaCoCo plugin is applied
            val jacocoPlugin = project.plugins.findPlugin("jacoco")
            if (jacocoPlugin != null) {
                project.logger.info("JaCoCo plugin detected, auto-configuring coverage analysis")

                // Auto-detect JaCoCo report task output
                val jacocoReportTask = project.tasks.findByName("jacocoTestReport")
                if (jacocoReportTask != null) {
                    extension.coverageExecFile.set(
                        project.layout.buildDirectory.file("jacoco/test.exec"),
                    )
                    project.logger.info("Auto-detected JaCoCo coverage file")
                }
            }
        } catch (e: Exception) {
            project.logger.warn("Could not auto-detect JaCoCo: ${e.message}")
        }
    }

    private fun resolveAndroidContext(
        project: Project,
        extension: MutationPluginExtension,
        mutationTask: org.gradle.api.tasks.TaskProvider<MutationTask>,
        resolver: AgpVariantResolver,
        variantCaptures: List<AgpVariantResolver.VariantCapture>,
    ) {
        try {
            if (!extension.isAndroid.getOrElse(false)) return
            val requestedVariant = extension.androidVariant.getOrElse("debug")
            val capture = resolver.findVariant(variantCaptures, requestedVariant)
            if (capture == null) {
                if (variantCaptures.isEmpty()) {
                    project.logger.info("No Android variants captured — skipping Android context")
                } else {
                    project.logger.warn(
                        "Android variant '$requestedVariant' not found. Available: " +
                            variantCaptures.joinToString { it.name },
                    )
                }
                return
            }
            val context =
                try {
                    resolver.buildContext(project, capture)
                } catch (e: Exception) {
                    // Variant resolution can fail when a project
                    // dependency exposes multiple runtime variants and
                    // the consumer's classpath is missing a flavor
                    // dimension attribute. AGP raises a structured
                    // `Cannot choose between the following variants`
                    // error naming the unmatched attribute; surface
                    // that to the user verbatim with a concrete fix.
                    val formatted = resolver.formatVariantResolutionError(e, capture, project)
                    if (formatted != null) {
                        throw org.gradle.api.GradleException(formatted, e)
                    }
                    // Unknown failure shape — rethrow the original.
                    throw e
                }
            extension.androidContext.set(context)
            mutationTask.configure { task ->
                task.dependsOn(context.compileTask, context.testCompileTask)
                // Wire the Android variant inputs onto the task as
                // @InputFiles / @InputFile so the build cache
                // invalidates when the variant classpath, classes
                // directories, or android.jar change. Without this the
                // Android values live on an @Internal holder and the
                // cache happily reuses stale results across SDK or
                // dependency upgrades.
                task.androidRuntimeClasspath.from(context.runtimeClasspath)
                task.androidTargetClasses.from(context.classesDirs)
                task.androidTestClasses.from(context.testClassesDirs)
                if (context.androidJar != null) {
                    task.androidJar.set(context.androidJar)
                }
            }
            project.logger.info(
                "Android variant '${context.variantName}' resolved " +
                    "(compile: ${context.compileTask}, test: ${context.testCompileTask})",
            )
        } catch (e: NoClassDefFoundError) {
            // AGP not on classpath — pure-JVM project, no variant context.
        } catch (e: org.gradle.api.GradleException) {
            // Re-throw GradleException (our formatted variant-resolution
            // errors) so the user sees the actionable message and
            // `caused by` chain instead of a wrapped warning.
            throw e
        } catch (e: Exception) {
            // Unknown failure shape — log at error level so misconfigurations
            // are visible, but skip the Android context so the build can
            // continue (other modules may still apply successfully).
            project.logger.error("Could not resolve Android variant context", e)
        }
    }

    /**
     * Reflectively register an `onVariants` callback for a non-app /
     * non-library Android plugin (Dynamic Feature, Test, Instant App).
     * The plugin class is resolved by name so this code compiles when
     * AGP is absent from the plugin's classpath (the `compileOnly` AGP
     * dep is the canonical case). If the class is not on the build
     * classpath, the registration is a silent no-op.
     */
    private fun registerAndroidVariantSubtype(
        project: Project,
        pluginClassName: String,
        pluginType: String,
        extension: MutationPluginExtension,
        agpVariantResolver: AgpVariantResolver,
        variantCaptures: MutableList<AgpVariantResolver.VariantCapture>,
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            val pluginClass = Class.forName(pluginClassName) as Class<out org.gradle.api.Plugin<*>>
            project.plugins.withType(pluginClass) {
                extension.isAndroid.set(true)
                extension.androidPluginType.set(pluginType)
                agpVariantResolver.registerOnVariants(project, variantCaptures)
            }
        } catch (_: ClassNotFoundException) {
            // AGP sub-plugin not on classpath (older AGP, or not applied).
        } catch (_: NoClassDefFoundError) {
            // Sub-plugin class linkage failure — also a no-op.
        }
    }

    /**
     * Discover the main and test Kotlin compile task names for this project.
     *
     * - Android: prefers the captured AGP variant data (from
     *   [AgpVariantResolver.registerOnVariants]) since those names are
     *   guaranteed to exist for the project's actual variant set. This
     *   avoids depending on hardcoded `compileDebugKotlin` /
     *   `compileDebugUnitTestKotlin` names that may not exist in
     *   release-only or custom variant configurations. When no variants
     *   are captured (e.g. tests with no real AGP applied) the function
     *   returns an empty pair and the wiring is deferred to
     *   [resolveAndroidContext] in afterEvaluate.
     * - Pure-JVM / KMP: prefers `compileKotlin` / `compileTestKotlin`
     *   (with the `Jvm` suffix variant for KMP). Falls back to the same
     *   names if nothing matches.
     *
     * @return Pair of (mainCompileTaskName, testCompileTaskName). Both
     *   strings are empty when no usable task name can be resolved (e.g.
     *   Android with no AGP variants captured).
     */
    internal fun discoverKotlinCompileTasks(
        project: Project,
        isAndroid: Boolean,
        variantCaptures: List<AgpVariantResolver.VariantCapture> = emptyList(),
    ): Pair<String, String> {
        // Android path: prefer captured variant data. The captures contain
        // exact task names (e.g. `compileReleaseKotlin` for a release-only
        // project) — hardcoded debug names would add bogus dependencies for
        // release-only or custom variant configurations.
        if (isAndroid) {
            if (variantCaptures.isNotEmpty()) {
                val debugCapture =
                    variantCaptures.firstOrNull {
                        it.name.equals("debug", ignoreCase = true)
                    }
                val capture = debugCapture ?: variantCaptures.first()
                return capture.compileTask to capture.testCompileTask
            }
            // No AGP variants captured. Do NOT fall back to hardcoded
            // `compileDebugKotlin` — the task may not exist (release-only
            // builds, custom variants). Return empty so the caller skips
            // wiring; the actual dependency is set up in afterEvaluate via
            // resolveAndroidContext when an Android context is resolved.
            return "" to ""
        }

        // Pure-JVM / KMP path: discover the standard Kotlin compile tasks.
        val kotlinCompiles =
            project.tasks
                .withType(KotlinCompile::class.java)
                .matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
                .map { it.name }
                .toList()

        val main =
            kotlinCompiles.firstOrNull { it == "compileKotlin" || it == "compileKotlinJvm" }
                ?: "compileKotlin"
        val test =
            kotlinCompiles.firstOrNull {
                it == "compileTestKotlin" || it == "compileTestKotlinJvm"
            }
                ?: "compileTestKotlin"
        return main to test
    }
}
