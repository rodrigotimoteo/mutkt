package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `mutates the variant's Kotlin and Java classes with its unit tests and reuses the configuration cache`() {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("build.gradle", library(classpath = mutktClasspath() + "\n" + agpClasspath() + "\n" + KGP, dsl = RUN_DSL))
        write(
            "src/main/kotlin/demo/Calc.kt",
            "package demo\n\nclass Calc {\n    fun add(a: Int, b: Int): Int = a + b\n}\n",
        )
        write("src/main/java/demo/Gate.java", GATE)
        write("src/test/java/demo/CalcTest.java", CALC_TEST)

        val first = runner("mutkt").build()
        val second = runner("mutkt").build()

        assertEquals(TaskOutcome.SUCCESS, first.task(":mutkt")?.outcome, first.output)
        assertEquals(null, first.task(":testDebugUnitTest"), "mutkt must not run the unit tests\n${first.output}")
        val report = dir.resolve("build/reports/mutkt/mutkt.txt").readText()
        assertTrue("Mutants: 2" in report && "KILLED       1" in report && "SURVIVED     1" in report, report)
        val json = dir.resolve("build/reports/mutkt/mutation-report.json").readText()
        assertTrue("\"demo/Calc.kt\"" in json && "\"demo/Gate.java\"" in json, json)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":mutkt")?.outcome, second.output)
        assertTrue("Reusing configuration cache" in second.output, second.output)
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
        |${if (KGP in classpath) "apply plugin: 'org.jetbrains.kotlin.android'" else ""}
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

    private fun runner(task: String = "help") =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withGradleVersion(GRADLE_VERSION)
            .withArguments(task, "--configuration-cache", "--console=plain", "--stacktrace", "--warning-mode=fail")

    private companion object {
        val KGP = "classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:${System.getProperty("mutkt.kotlinVersion")}'"

        val JUNIT_JARS = Regex("^(junit-jupiter|junit-platform-(engine|commons|launcher)|opentest4j|apiguardian)")

        // Jupiter from this JVM's jars; the arithmetic mutant in Calc is killed, the boundary one in Gate survives.
        val RUN_DSL =
            """
            repositories {
                google()
                mavenCentral()
            }
            android {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions { unitTests.all { useJUnitPlatform() } }
            }
            kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
            dependencies { testImplementation files(${TestKitProject(File(".")).jars(JUNIT_JARS)}) }
            mutkt { operators = ['arithmetic', 'conditionals-boundary'] }
            """.trimIndent()

        const val GATE =
            "package demo;\npublic class Gate {\n    public boolean isAdult(int age) { return age >= 18; }\n}\n"

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

                @Test
                void adult() {
                    Assertions.assertTrue(new Gate().isAdult(30));
                }
            }
            """.trimIndent()

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
