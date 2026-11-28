package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.Properties

/** A throwaway Android build on real AGP for `agpTest`, which sets the AGP and Gradle versions and the TestKit dir. */
internal class AndroidBuild(
    private val dir: File,
) {
    // Both plugins go on one buildscript classpath, as with a plugins { } block, so MutKt sees AGP's classes.
    // AGP 9 gives unit tests only to the tested build type (8.4 has no such option): keep both, as every cell expects.
    fun setUp(dsl: String) {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("gradle.properties", "android.onlyEnableUnitTestForTheTestedBuildType=false\n")
        write("build.gradle", library(classpath = mutktClasspath() + "\n" + agpClasspath(), dsl = dsl))
    }

    fun library(
        classpath: String,
        dsl: String,
        plugin: String = "com.android.library",
        namespace: String = "demo",
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
        |apply plugin: '$plugin'
        |${if (KGP in classpath && !BUILT_IN_KOTLIN) "apply plugin: 'org.jetbrains.kotlin.android'" else ""}
        |apply plugin: 'io.github.rodrigotimoteo.mutkt'
        |android {
        |    namespace = '$namespace'
        |    compileSdk = 34
        |}
        |$dsl
        """.trimMargin()

    fun mutktClasspath() = "classpath files(${MUTKT_CLASSPATH.joinToString { "'${it.invariantSeparatorsPath}'" }})"

    fun agpClasspath() = "classpath 'com.android.tools.build:gradle:$AGP_VERSION'"

    fun write(
        path: String,
        text: String,
    ) = dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun androidUserHome() = dir.resolve(".android").apply { resolve("cache").mkdirs() }

    fun runner(task: String = "help"): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withTestKitDir(TEST_KIT_DIR)
            // AGP 8 creates its user home's cache in the first build, which the configuration cache sees as a change.
            .withEnvironment(System.getenv() + ("ANDROID_USER_HOME" to androidUserHome().absolutePath))
            .withGradleVersion(GRADLE_VERSION)
            .withArguments(task, "--configuration-cache", "--console=plain", "--stacktrace", "--warning-mode=fail")

    companion object {
        val KGP = "classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:${System.getProperty("mutkt.kotlinVersion")}'"

        const val REPOSITORIES = "repositories {\n    google()\n    mavenCentral()\n}"

        /** Jupiter from this JVM's jars. */
        val JUPITER_JARS: String =
            TestKitProject(
                File("."),
            ).jars(Regex("^(junit-jupiter|junit-platform-(engine|commons|launcher)|opentest4j|apiguardian)"))

        val AGP_VERSION: String = checkNotNull(System.getProperty("mutkt.agpVersion")) { "mutkt.agpVersion is not set" }

        // AGP 9 compiles Kotlin itself and rejects the Kotlin Android plugin; KGP on the classpath sets its version.
        val BUILT_IN_KOTLIN = AGP_VERSION.substringBefore('.').toInt() >= 9
        private val GRADLE_VERSION: String =
            checkNotNull(System.getProperty("mutkt.agpGradleVersion")) { "mutkt.agpGradleVersion is not set" }

        val TEST_KIT_DIR = File(checkNotNull(System.getProperty("mutkt.testKitDir")) { "mutkt.testKitDir is not set" })

        // What withPluginClasspath() would inject: the plugin and its runtime dependencies.
        private val MUTKT_CLASSPATH: List<File> =
            Properties()
                .apply {
                    AndroidBuild::class.java
                        .getResourceAsStream("/plugin-under-test-metadata.properties")
                        .use(::load)
                }.getProperty("implementation-classpath")
                .split(File.pathSeparator)
                .map(::File)
    }
}
