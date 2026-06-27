package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidJarLocatorTest {
    /**
     * Write a minimal but valid ZIP that passes [AndroidJarLocator]'s
     * `isValidAndroidJar` check (must contain an `android/Manifest.class`
     * entry). Tests previously used a 2-byte "ZIP magic" stub that
     * happened to bypass the original (pre-validation) `exists()` check;
     * with the proper ZIP validation in place, the stub is rejected.
     */
    private fun writeStubAndroidJar(file: File) {
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.putNextEntry(ZipEntry("android/Manifest.class"))
                zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                zos.closeEntry()
            }
        }
    }

    @Test
    fun `returns null when nothing is configured and no SDK is present`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = null,
                targetSdk = null,
                envProvider = { null },
                homeSupplier = { "/nonexistent-home-${System.nanoTime()}" },
            )
        assertThat(result).isNull()
    }

    @Test
    fun `finds android_jar under ANDROID_HOME when env var is set`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("android.jar")
        assertThat(result.absolutePath)
            .isEqualTo(File(sdkDir, "platforms/android-34/android.jar").absolutePath)
    }

    @Test
    fun `finds android_jar under ANDROID_SDK_ROOT when ANDROID_HOME is missing`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "34",
                targetSdk = null,
                envProvider = { key -> if (key == "ANDROID_SDK_ROOT") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath)
            .isEqualTo(File(sdkDir, "platforms/android-34/android.jar").absolutePath)
    }

    @Test
    fun `finds android_jar from local_properties when env vars are missing`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        File(projectDir, "local.properties")
            .writeText("sdk.dir=${sdkDir.absolutePath}\nother.key=value")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath)
            .isEqualTo(File(sdkDir, "platforms/android-34/android.jar").absolutePath)
    }

    @Test
    fun `finds android_jar under home directory android-sdk`(
        @TempDir tempDir: Path,
    ) {
        val fakeHome = tempDir.resolve("fake-home").toFile().apply { mkdirs() }
        val sdkDir = File(fakeHome, ".android-sdk").apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { null },
                homeSupplier = { fakeHome.absolutePath },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath)
            .isEqualTo(File(sdkDir, "platforms/android-34/android.jar").absolutePath)
    }

    @Test
    fun `finds android_jar under projectRoot platforms as last resort`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val platforms = File(projectDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.canonicalPath)
            .isEqualTo(File(platforms, "android.jar").canonicalPath)
    }

    @Test
    fun `uses targetSdk when compileSdk is null`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-33").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = null,
                targetSdk = 33,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath).contains("android-33")
    }

    @Test
    fun `falls back to android-34 when neither compileSdk nor targetSdk is provided`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = null,
                targetSdk = null,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath).contains("android-34")
    }

    @Test
    fun `normalizes numeric compileSdk to android-prefixed form`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-35").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "35",
                targetSdk = null,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath).contains("android-35")
    }

    @Test
    fun `returns null when SDK dir exists but android_jar for the target version is missing`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        File(sdkDir, "platforms").mkdirs()
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNull()
    }

    @Test
    fun `findAndroidSdkDir returns ANDROID_HOME when env var is set`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { key -> if (key == "ANDROID_HOME") sdkDir.absolutePath else null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isEqualTo(sdkDir)
    }

    @Test
    fun `findAndroidSdkDir returns sdk dir from local_properties`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        File(projectDir, "local.properties").writeText("sdk.dir=${sdkDir.absolutePath}")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { null },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isEqualTo(sdkDir)
    }

    @Test
    fun `findAndroidSdkDir returns home android-sdk when no other source is set`(
        @TempDir tempDir: Path,
    ) {
        val fakeHome = tempDir.resolve("fake-home").toFile().apply { mkdirs() }
        val sdkDir = File(fakeHome, ".android-sdk").apply { mkdirs() }
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { null },
                homeSupplier = { fakeHome.absolutePath },
            )
        assertThat(result).isEqualTo(sdkDir)
    }

    @Test
    fun `findAndroidSdkDir returns null when no source is configured`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { null },
                homeSupplier = { "/nonexistent-home-${System.nanoTime()}" },
            )
        assertThat(result).isNull()
    }

    @Test
    fun `ANDROID_HOME takes priority over ANDROID_SDK_ROOT`(
        @TempDir tempDir: Path,
    ) {
        val homeSdk = tempDir.resolve("home").toFile().apply { mkdirs() }
        val rootSdk = tempDir.resolve("root").toFile().apply { mkdirs() }
        val project =
            ProjectBuilder.builder()
                .withProjectDir(tempDir.resolve("proj").toFile())
                .build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { key ->
                    when (key) {
                        "ANDROID_HOME" -> homeSdk.absolutePath
                        "ANDROID_SDK_ROOT" -> rootSdk.absolutePath
                        else -> null
                    }
                },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isEqualTo(homeSdk)
    }

    @Test
    fun `local_properties takes priority over home android-sdk`(
        @TempDir tempDir: Path,
    ) {
        val sdkDir = tempDir.resolve("sdk").toFile().apply { mkdirs() }
        val fakeHome = tempDir.resolve("fake-home").toFile().apply { mkdirs() }
        File(fakeHome, ".android-sdk").mkdirs()
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        File(projectDir, "local.properties").writeText("sdk.dir=${sdkDir.absolutePath}")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.findAndroidSdkDir(
                project = project,
                envProvider = { null },
                homeSupplier = { fakeHome.absolutePath },
            )
        assertThat(result).isEqualTo(sdkDir)
    }

    @Test
    fun `resolveTargetSdk returns android-34 by default`() {
        val locator = AndroidJarLocator()
        assertThat(locator.resolveTargetSdk(null, null)).isEqualTo("android-34")
    }

    @Test
    fun `resolveTargetSdk uses targetSdk as android-prefixed when compileSdk is null`() {
        val locator = AndroidJarLocator()
        assertThat(locator.resolveTargetSdk(null, 33)).isEqualTo("android-33")
    }

    @Test
    fun `resolveTargetSdk passes through android-prefixed compileSdk`() {
        val locator = AndroidJarLocator()
        assertThat(locator.resolveTargetSdk("android-35", null)).isEqualTo("android-35")
    }

    @Test
    fun `resolveTargetSdk normalizes numeric compileSdk`() {
        val locator = AndroidJarLocator()
        assertThat(locator.resolveTargetSdk("35", null)).isEqualTo("android-35")
    }

    @Test
    fun `find uses projectRoot fallback when SDK env points to non-existent dir`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val platforms = File(projectDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { "/nonexistent-sdk-path-${System.nanoTime()}" },
                homeSupplier = { "/nonexistent-home" },
            )
        assertThat(result).isNotNull
        assertThat(result!!.canonicalPath)
            .isEqualTo(File(platforms, "android.jar").canonicalPath)
    }

    @Test
    fun `find skips local_properties when sdk_dir points to non-existent path and falls through to home`(
        @TempDir tempDir: Path,
    ) {
        val fakeHome = tempDir.resolve("fake-home").toFile().apply { mkdirs() }
        val sdkDir = File(fakeHome, ".android-sdk").apply { mkdirs() }
        val platforms = File(sdkDir, "platforms/android-34").apply { mkdirs() }
        writeStubAndroidJar(File(platforms, "android.jar"))
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        File(projectDir, "local.properties")
            .writeText("sdk.dir=/does/not/exist/${System.nanoTime()}")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result =
            locator.find(
                project = project,
                compileSdk = "android-34",
                targetSdk = null,
                envProvider = { null },
                homeSupplier = { fakeHome.absolutePath },
            )
        assertThat(result).isNotNull
        assertThat(result!!.absolutePath)
            .isEqualTo(File(sdkDir, "platforms/android-34/android.jar").absolutePath)
    }

    @Test
    fun `findRobolectricSdk reads sdk from src-test-resources robolectric properties`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val resourcesDir = File(projectDir, "src/test/resources").apply { mkdirs() }
        File(resourcesDir, "robolectric.properties").writeText("sdk=33\nother=ignored")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isEqualTo("android-33")
    }

    @Test
    fun `findRobolectricSdk falls back to project-root robolectric properties`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        File(projectDir, "robolectric.properties").writeText("sdk=30")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isEqualTo("android-30")
    }

    @Test
    fun `findRobolectricSdk returns null when no robolectric properties file is present`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isNull()
    }

    @Test
    fun `findRobolectricSdk returns null when sdk key is missing from properties`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val resourcesDir = File(projectDir, "src/test/resources").apply { mkdirs() }
        File(resourcesDir, "robolectric.properties").writeText("other.key=irrelevant")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isNull()
    }

    @Test
    fun `findRobolectricSdk returns null when sdk value is not a valid integer`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val resourcesDir = File(projectDir, "src/test/resources").apply { mkdirs() }
        File(resourcesDir, "robolectric.properties").writeText("sdk=not-a-number")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isNull()
    }

    @Test
    fun `findRobolectricSdk tolerates whitespace around the sdk value`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val resourcesDir = File(projectDir, "src/test/resources").apply { mkdirs() }
        File(resourcesDir, "robolectric.properties").writeText("sdk=  28  ")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isEqualTo("android-28")
    }

    @Test
    fun `findRobolectricSdk prefers resources over project-root file`(
        @TempDir tempDir: Path,
    ) {
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val resourcesDir = File(projectDir, "src/test/resources").apply { mkdirs() }
        File(resourcesDir, "robolectric.properties").writeText("sdk=33")
        File(projectDir, "robolectric.properties").writeText("sdk=29")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val locator = AndroidJarLocator()
        val result = locator.findRobolectricSdk(project)
        assertThat(result).isEqualTo("android-33")
    }
}
