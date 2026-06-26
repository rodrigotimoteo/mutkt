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
                val (compileTaskName, testCompileTaskName) =
                    discoverKotlinCompileTasks(project, isAndroid, variantCaptures)
                // Only wire dependencies for tasks that actually exist or are
                // standard pure-JVM defaults. For Android with no AGP variant
                // capture, the wiring is deferred to afterEvaluate where
                // resolveAndroidContext re-wires with the correct names.
                if (compileTaskName.isNotEmpty() && testCompileTaskName.isNotEmpty()) {
                    task.dependsOn(compileTaskName, testCompileTaskName)
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
            val context = resolver.buildContext(project, capture)
            extension.androidContext.set(context)
            mutationTask.configure { task ->
                task.dependsOn(context.compileTask, context.testCompileTask)
            }
            project.logger.info(
                "Android variant '${context.variantName}' resolved " +
                    "(compile: ${context.compileTask}, test: ${context.testCompileTask})",
            )
        } catch (e: NoClassDefFoundError) {
            // AGP not on classpath — pure-JVM project, no variant context.
        } catch (e: Exception) {
            project.logger.warn("Could not resolve Android variant context: ${e.message}")
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
    ): Pair<String, String> = discoverKotlinCompileTasksInternal(project, isAndroid, variantCaptures)

    private fun discoverKotlinCompileTasksInternal(
        project: Project,
        isAndroid: Boolean,
        variantCaptures: List<AgpVariantResolver.VariantCapture>,
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
