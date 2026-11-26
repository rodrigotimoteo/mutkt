package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/** Android library builds on real AGP; run by `agpTest`, which needs an SDK (ANDROID_HOME) and the network. */
@Tag("agp")
class MutKtAndroidVariantTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `the default debug variant passes configuration`() {
        setUp("")

        runner().build()
    }

    @Test
    fun `another variant with unit tests passes configuration`() {
        setUp(
            """
            mutkt { variant = 'release' }
            androidComponents { beforeVariants(selector().withName('debug')) { it.enableUnitTest = false } }
            """.trimIndent(),
        )

        runner().build()
    }

    @Test
    fun `a missing variant fails with the variants that have unit tests`() {
        setUp("mutkt { variant = 'nope' }")

        val output = runner().buildAndFail().output

        assertTrue("MutKt cannot find variant 'nope'; variants with unit tests: debug, release" in output, output)
    }

    @Test
    fun `the default variant without unit tests fails with the variants that have them`() {
        setUp("androidComponents { beforeVariants(selector().withName('debug')) { it.enableUnitTest = false } }")

        val output = runner().buildAndFail().output

        assertTrue("MutKt variant 'debug' has no unit tests; variants with unit tests: release" in output, output)
    }

    @Test
    fun `AGP in a child scope of MutKt fails with where to declare it`() {
        write("settings.gradle", "rootProject.name = 'sample'\ninclude 'lib'\n")
        write("build.gradle", "buildscript { dependencies { ${mutktClasspath()} } }\n")
        write("lib/build.gradle", library(classpath = agpClasspath(), dsl = ""))

        val output = runner().buildAndFail().output

        assertTrue("MutKt cannot load the Android Gradle plugin's classes" in output, output)
    }

    // Both plugins go on one buildscript classpath, as with a plugins { } block, so MutKt sees AGP's classes.
    private fun setUp(dsl: String) {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("build.gradle", library(classpath = mutktClasspath() + "\n" + agpClasspath(), dsl = dsl))
    }

    private fun library(
        classpath: String,
        dsl: String,
    ) = """
        |buildscript {
        |    repositories {
        |        google()
        |        mavenCentral()
        |    }
        |    dependencies {
        |        $classpath
        |    }
        |}
        |apply plugin: 'com.android.library'
        |apply plugin: 'io.github.rodrigotimoteo.mutkt'
        |android {
        |    namespace = 'demo'
        |    compileSdk = 34
        |}
        |$dsl
        """.trimMargin()

    private fun mutktClasspath() =
        "classpath files(${MUTKT_CLASSPATH.joinToString { "'${it.invariantSeparatorsPath}'" }})"

    private fun agpClasspath() = "classpath 'com.android.tools.build:gradle:$AGP_VERSION'"

    private fun write(
        path: String,
        text: String,
    ) = dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun runner() =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withGradleVersion(GRADLE_VERSION)
            .withArguments("help", "--configuration-cache", "--console=plain", "--stacktrace", "--warning-mode=fail")

    private companion object {
        val AGP_VERSION: String = checkNotNull(System.getProperty("mutkt.agpVersion")) { "mutkt.agpVersion is not set" }
        val GRADLE_VERSION: String =
            checkNotNull(System.getProperty("mutkt.agpGradleVersion")) { "mutkt.agpGradleVersion is not set" }

        // What withPluginClasspath() would inject: the plugin and its runtime dependencies.
        val MUTKT_CLASSPATH: List<File> =
            Properties()
                .apply {
                    MutKtAndroidVariantTest::class.java
                        .getResourceAsStream("/plugin-under-test-metadata.properties")
                        .use(::load)
                }.getProperty("implementation-classpath")
                .split(File.pathSeparator)
                .map(::File)
    }
}
