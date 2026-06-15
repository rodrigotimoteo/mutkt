package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Project
import java.io.File

/**
 * Resolves the path to `android.jar` for an Android project.
 *
 * Searches, in priority order:
 *
 *  1. `$ANDROID_HOME/platforms/android-<target>/android.jar`
 *  2. `$ANDROID_SDK_ROOT/platforms/android-<target>/android.jar`
 *  3. `<local.properties sdk.dir>/platforms/android-<target>/android.jar`
 *  4. `~/.android-sdk/platforms/android-<target>/android.jar`
 *  5. `<projectRoot>/platforms/android-<target>/android.jar` (last resort)
 *
 * Where `<target>` is derived from `compileSdk` (preferred) or `targetSdk`,
 * defaulting to `android-34` when neither is provided.
 *
 * Returns `null` when no candidate exists on disk. Callers should log a
 * clear warning so users can wire `android.jar` manually via
 * `mutationTest { classpath.setFrom(...) }`.
 *
 * The [envProvider] and [homeSupplier] parameters are injected so the
 * search can be exercised deterministically from unit tests without
 * touching the real process environment.
 */
internal class AndroidJarLocator {
    fun find(
        project: Project,
        compileSdk: String?,
        targetSdk: Int?,
        envProvider: (String) -> String? = System::getenv,
        homeSupplier: () -> String = { System.getProperty("user.home") },
    ): File? {
        val target = resolveTargetSdk(compileSdk, targetSdk)
        val sdkDir = findAndroidSdkDir(project, envProvider, homeSupplier)
        if (sdkDir != null) {
            val jar = File(sdkDir, "platforms/$target/android.jar")
            if (jar.exists()) return jar
        }
        val projectRoot = project.rootProject.projectDir
        val projectRootJar = File(projectRoot, "platforms/$target/android.jar")
        return if (projectRootJar.exists()) projectRootJar else null
    }

    fun findAndroidSdkDir(
        project: Project,
        envProvider: (String) -> String? = System::getenv,
        homeSupplier: () -> String = { System.getProperty("user.home") },
    ): File? {
        val env = envProvider("ANDROID_HOME") ?: envProvider("ANDROID_SDK_ROOT")
        if (env != null && File(env).exists()) return File(env)

        val localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            val sdkLine = localProps.readLines().firstOrNull { it.startsWith("sdk.dir=") }
            val sdkDir = sdkLine?.removePrefix("sdk.dir=")?.trim()
            if (!sdkDir.isNullOrEmpty() && File(sdkDir).exists()) return File(sdkDir)
        }

        val homeSdk = File(homeSupplier(), ".android-sdk")
        if (homeSdk.exists()) return homeSdk

        return null
    }

    internal fun resolveTargetSdk(
        compileSdk: String?,
        targetSdk: Int?,
    ): String {
        if (!compileSdk.isNullOrBlank()) {
            if (compileSdk.startsWith("android-")) {
                return compileSdk
            }
            return "android-$compileSdk"
        }
        if (targetSdk != null) return "android-$targetSdk"
        return "android-34"
    }
}
