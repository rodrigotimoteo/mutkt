package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers the `mutkt` extension and the `mutkt` task, for the java plugin or for one Android variant. */
public class MutKtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val mutkt = project.extensions.create("mutkt", MutKtExtension::class.java)
        mutkt.timeoutMs.convention(DEFAULT_TIMEOUT_MS)
        mutkt.engines.convention(DEFAULT_ENGINES)
        mutkt.reports.console.convention(true)
        mutkt.variant.convention("debug")

        // Only `java` creates source sets: a java-base-only project passes the check below but has no model.
        project.pluginManager.withPlugin("java") {
            project.registerMutktModel()
            project.registerMutktTask(mutkt)
        }

        ANDROID_PLUGINS.forEach { id -> project.pluginManager.withPlugin(id) { project.activateAndroid(mutkt) } }

        var jvmPluginApplied = false
        // The Kotlin JVM and Android plugins apply java-base too, so one hook covers all three.
        project.pluginManager.withPlugin("java-base") { jvmPluginApplied = true }
        // Plugin order is free, so the check can only run once the build script has been evaluated.
        project.afterEvaluate {
            if (!jvmPluginApplied) throw GradleException("MutKt requires the Java, Kotlin JVM or Android plugin")
        }
    }

    // A separate function, so AGP's classes load only when an Android plugin is applied. MutKt's class loader sees
    // them only when AGP is declared in the same or a parent plugins { } scope.
    private fun Project.activateAndroid(mutkt: MutKtExtension) =
        try {
            wireAndroid(mutkt)
        } catch (missing: NoClassDefFoundError) {
            throw GradleException(
                "MutKt cannot load the Android Gradle plugin's classes: declare the Android Gradle plugin in " +
                    "the same plugins { } block as MutKt or in a parent project's " +
                    "(for example in the root with apply false)",
                missing,
            )
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
        val ANDROID_PLUGINS = listOf("com.android.application", "com.android.library")
    }
}
