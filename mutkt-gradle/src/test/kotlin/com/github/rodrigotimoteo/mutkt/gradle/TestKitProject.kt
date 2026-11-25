package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A throwaway Gradle build (Java plugin, Jupiter tests) that applies the plugin under test.
 * JUnit and the annotations come from this JVM's jars, so builds run offline.
 */
internal class TestKitProject(
    private val dir: File,
    gradleVersion: String = CURRENT_GRADLE,
) {
    private val version = gradleVersion.takeUnless { it == CURRENT_GRADLE }

    val report: File get() = dir.resolve("build/reports/mutkt/mutkt.txt")

    /** Writes the settings and build files, then `sources` under src/main/java and `tests` under src/test/java. */
    fun setUp(
        dsl: String = "",
        sources: Map<String, String> = mapOf("demo/Calc.java" to calc("a + b")),
        tests: Map<String, String> = mapOf("demo/CalcTest.java" to CALC_TEST),
    ) {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=512m\n")
        write(
            "build.gradle",
            """
            plugins {
                id 'java'
                id 'io.github.rodrigotimoteo.mutkt'
            }
            dependencies {
                compileOnly files(${jars(ANNOTATIONS_JAR)})
                testImplementation files(${jars(TEST_JARS)})
            }
            $dsl
            """.trimIndent(),
        )
        sources.forEach { (path, text) -> write("src/main/java/$path", text) }
        tests.forEach { (path, text) -> write("src/test/java/$path", text) }
    }

    fun write(
        path: String,
        text: String,
    ) = dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    fun build(): BuildResult = runner().build()

    fun buildAndFail(): BuildResult = runner().buildAndFail()

    private fun runner() =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments("mutkt", "--configuration-cache", "--console=plain", "--offline", "--stacktrace")
            .also { runner -> version?.let(runner::withGradleVersion) }

    private fun jars(pattern: Regex): String =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter { it.isFile && pattern.containsMatchIn(it.name) }
            .joinToString { "'${it.absolutePath}'" }

    companion object {
        const val CURRENT_GRADLE = "current"

        private val ANNOTATIONS_JAR = Regex("^mutkt-annotations")
        private val TEST_JARS =
            Regex("^(junit-jupiter|junit-platform-(engine|commons)|opentest4j|apiguardian|kotlin-stdlib)")

        val CALC_TEST =
            """
            package demo;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;

            public class CalcTest {
                @Test
                void adds() {
                    Assertions.assertEquals(5, new Calc().add(2, 3));
                }
            }
            """.trimIndent()

        fun calc(expression: String) =
            "package demo;\npublic class Calc {\n    public int add(int a, int b) { return $expression; }\n}\n"
    }
}
