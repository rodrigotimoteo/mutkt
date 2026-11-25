package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers the `mutkt` extension and, with the java plugin, the `mutkt` task. */
public class MutKtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val mutkt = project.extensions.create("mutkt", MutKtExtension::class.java)
        mutkt.timeoutMs.convention(DEFAULT_TIMEOUT_MS)
        mutkt.engines.convention(DEFAULT_ENGINES)
        mutkt.reports.console.convention(true)

        // Only `java` creates source sets: a java-base-only project passes the check below but has no model.
        project.pluginManager.withPlugin("java") {
            project.registerMutktModel()
            project.registerMutktTask(mutkt)
        }

        var jvmPluginApplied = false
        // The Kotlin JVM plugin applies java-base too, so one hook covers both.
        project.pluginManager.withPlugin("java-base") { jvmPluginApplied = true }
        // Plugin order is free, so the check can only run once the build script has been evaluated.
        project.afterEvaluate {
            if (!jvmPluginApplied) throw GradleException("MutKt requires the Java or Kotlin JVM plugin")
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
