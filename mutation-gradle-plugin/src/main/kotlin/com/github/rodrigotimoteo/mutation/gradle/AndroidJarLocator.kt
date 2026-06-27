package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Project
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

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
 * defaulting to `android-34` when neither is provided. A Robolectric
 * `sdk=` setting (in `robolectric.properties`) overrides `compileSdk`
 * so the test runtime and the mutation classloader agree on the
 * android.jar version.
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
    companion object {
        /**
         * Default Android API level used when neither `compileSdk` nor
         * `targetSdk` is provided by the consuming project. Matches
         * the most recent stable Android SDK that AGP installs by
         * default; bump in lockstep with the project's AGP baseline.
         */
        private const val DEFAULT_SDK = 34
    }

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
            if (isValidAndroidJar(jar)) return jar
        }
        val projectRoot = project.rootProject.projectDir
        val projectRootJar = File(projectRoot, "platforms/$target/android.jar")
        return if (isValidAndroidJar(projectRootJar)) projectRootJar else null
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
            // Use java.util.Properties for parsing so that:
            //  - escaped continuations (`sdk.dir=\\path\\with spaces`) work
            //  - comments (`# ...` and `! ...`) are skipped
            //  - leading/trailing whitespace is handled by the parser
            //  - alternate key spellings (`sdk-dir`, quoted values) are
            //    tolerated the way the rest of the Gradle ecosystem
            //    handles local.properties.
            val props = Properties()
            localProps.inputStream().use { props.load(it) }
            val sdkDir = props.getProperty("sdk.dir")?.trim()
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
        return "android-$DEFAULT_SDK"
    }

    /**
     * Probe `robolectric.properties` for an explicit `sdk=NN` value.
     *
     * Robolectric binds a specific Android SDK level for shadowing
     * purposes; tests are run against that SDK even when the project's
     * `compileSdk` is newer. Returning the Robolectric SDK here lets
     * the resolver pick the matching `android.jar` so the mutant
     * classloader agrees with the test runtime.
     *
     * Lookup locations (first match wins):
     *  - `<projectDir>/src/test/resources/robolectric.properties`
     *  - `<projectDir>/robolectric.properties` (user-level override)
     *
     * Returns `null` when no file is present, the file has no `sdk=`
     * key, or the value cannot be parsed as an Int. Callers fall back
     * to `compileSdk` in that case. The method never throws — a
     * missing or malformed file is treated as "no override".
     */
    internal fun findRobolectricSdk(project: Project): String? {
        val candidates =
            listOf(
                File(project.projectDir, "src/test/resources/robolectric.properties"),
                File(project.projectDir, "robolectric.properties"),
            )
        for (file in candidates) {
            if (!file.exists()) continue
            val props = Properties()
            try {
                file.inputStream().use { props.load(it) }
            } catch (_: Exception) {
                continue
            }
            val raw = props.getProperty("sdk") ?: continue
            val sdk = raw.trim().toIntOrNull() ?: continue
            return "android-$sdk"
        }
        return null
    }

    /**
     * Validate that [jar] is a readable zip whose entries include
     * `android/Manifest.class` (the canonical android.jar marker).
     * Plain `exists()` returns true for truncated downloads, partially
     * written files, and other garbage that would explode later in
     * the mutant classloader with a `ZipException`. Returning false
     * from the validator lets callers fall through to the next
     * candidate location.
     */
    private fun isValidAndroidJar(jar: File): Boolean {
        if (!jar.exists() || !jar.isFile) return false
        return try {
            ZipFile(jar).use { zip ->
                // `zip.entries()` is the modern Java 17+ replacement for
                // the legacy `ZipFile.getNextEntry` API used in older
                // AGP-jar probes. The legacy method was removed in Java
                // 17, so the ZipFile iteration has to go through the
                // Iterator API instead.
                val iterator = zip.entries()
                generateSequence { if (iterator.hasMoreElements()) iterator.nextElement() else null }
                    .any { it.name == "android/Manifest.class" }
            }
        } catch (_: Exception) {
            false
        }
    }
}
