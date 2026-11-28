package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.assertSummaryLines
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A Jupiter project without `junit-platform-launcher` (Gradle 9 adds none): MutKt resolves the launcher at the
 * version of the project's engine API from the project's repositories.
 */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtPlatformLauncherTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `the launcher is resolved at the engine's version and runs the tests`(version: String) {
        val project = setUp(version, publishLauncher = true)

        val result = project.build("mutkt")
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        assertSummaryLines(project.report.readText(), "Score: 100.0%")
        val again = project.build("mutkt").output
        assertTrue("Reusing configuration cache" in again, again)
        val classpath = project.build("mutktClasspath").output
        assertTrue("junit-platform-launcher-$ENGINE_VERSION.jar" in classpath, classpath)
    }

    // Without the configuration cache Gradle queries the classpath several times; the lookup and its warning run once.
    @OnGradleVersions
    fun `a launcher that cannot be resolved falls back to the bundled Platform with one warning`(version: String) {
        val project = setUp(version, publishLauncher = false)

        val result = project.build("mutkt", configurationCache = false)
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        assertSummaryLines(project.report.readText(), "Score: 100.0%")
        val line = "add testRuntimeOnly(\"org.junit.platform:junit-platform-launcher:$ENGINE_VERSION\")"
        assertEquals(1, result.output.windowed(line.length).count { it == line }, result.output)
    }

    // The engine is published at a version no real launcher has, so only a lookup at the engine's version finds it.
    private fun setUp(
        version: String,
        publishLauncher: Boolean,
    ): TestKitProject {
        val project = TestKitProject(dir, version)
        project.setUp()
        val repo = dir.resolve("repo")
        publish(repo, "junit-platform-engine", ENGINE_JAR)
        if (publishLauncher) publish(repo, "junit-platform-launcher", LAUNCHER_JAR)
        project.write(
            "build.gradle",
            """
            plugins {
                id 'java'
                id 'io.github.rodrigotimoteo.mutkt'
            }
            repositories {
                maven { url = '${repo.toURI()}' }
            }
            dependencies {
                testImplementation files(${project.jars(TEST_JARS)})
                testImplementation 'org.junit.platform:junit-platform-engine:$ENGINE_VERSION'
            }
            tasks.named('test') { useJUnitPlatform() }
            mutkt { operators = ['arithmetic'] }
            def mutktClasspath = tasks.named('mutkt').map { it.testRuntimeClasspath }
            tasks.register('mutktClasspath') {
                def files = mutktClasspath
                doLast { println files.get().files*.name }
            }
            """.trimIndent(),
        )
        return project
    }

    // A module without dependencies: the other Platform jars are on the classpath as files.
    private fun publish(
        repo: File,
        module: String,
        jar: Regex,
    ) {
        val dir = repo.resolve("org/junit/platform/$module/$ENGINE_VERSION").apply { mkdirs() }
        jarOnClasspath(jar).copyTo(dir.resolve("$module-$ENGINE_VERSION.jar"))
        dir.resolve("$module-$ENGINE_VERSION.pom").writeText(
            "<project><modelVersion>4.0.0</modelVersion><groupId>org.junit.platform</groupId>" +
                "<artifactId>$module</artifactId><version>$ENGINE_VERSION</version></project>\n",
        )
    }

    private fun jarOnClasspath(pattern: Regex): File =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .first { it.isFile && pattern.containsMatchIn(it.name) }

    private companion object {
        const val ENGINE_VERSION = "1.99.0"
        val ENGINE_JAR = Regex("^junit-platform-engine")
        val LAUNCHER_JAR = Regex("^junit-platform-launcher")
        val TEST_JARS = Regex("^(junit-jupiter|junit-platform-commons|opentest4j|apiguardian)")
    }
}
