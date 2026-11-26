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
    fun `resolves AGP into the TestKit dir that CI caches`() {
        setUp("")

        runner().build()

        val agp = TEST_KIT_DIR.resolve("caches/modules-2/files-2.1/com.android.tools.build/gradle/$AGP_VERSION")
        assertTrue(agp.isDirectory, "$agp is missing")
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
    fun `skips generated classes by default, and excludedClasses extends or replaces the defaults`() {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("gradle.properties", "android.useAndroidX=true\n")
        write("src/main/res/layout/activity_main.xml", LAYOUT)
        write("src/main/java/demo/Gate.java", GATE)
        write("src/main/java/demo/Old.java", GATE.replace("Gate", "Old"))
        write("src/test/java/demo/GateTest.java", GATE_TEST)
        val classpath = mutktClasspath() + "\n" + agpClasspath()
        val json = dir.resolve("build/reports/mutkt/mutation-report.json")

        write("build.gradle", library(classpath, BINDING_DSL + "\nmutkt { excludedClasses.add('demo.Old') }"))
        runner("mutkt").build()
        val extended = json.readText()
        write("build.gradle", library(classpath, BINDING_DSL + "\nmutkt { excludedClasses = [] }"))
        runner("mutkt").build()
        val replaced = json.readText()

        assertTrue("\"demo/Gate.java\"" in extended, extended)
        assertTrue("ActivityMainBinding" !in extended && "\"demo/Old.java\"" !in extended, extended)
        assertTrue("ActivityMainBinding" in replaced && "\"demo/Old.java\"" in replaced, replaced)
    }

    @Test
    fun `an app mutant is killed by tests that need an Android library module and an AAR, and the library runs too`() {
        write("settings.gradle", "rootProject.name = 'sample'\ninclude 'app', 'lib'\n")
        write("gradle.properties", "android.useAndroidX=true\n")
        val plugins = "dependencies {\n${mutktClasspath()}\n${agpClasspath()}\n}"
        write("build.gradle", "buildscript {\n$REPOSITORIES\n$plugins\n}\n")
        write("lib/build.gradle", library("", MODULE_DSL, namespace = "demo.lib"))
        write("lib/src/main/java/demo/lib/Base.java", BASE)
        write("lib/src/test/java/demo/lib/BaseTest.java", BASE_TEST)
        write("app/build.gradle", library("", APP_DSL, "com.android.application", "demo.app"))
        write("app/src/main/AndroidManifest.xml", "<manifest />\n")
        write("app/src/main/java/demo/app/Score.java", SCORE)
        write("app/src/test/java/demo/app/ScoreTest.java", SCORE_TEST)

        val result = runner("mutkt").build()

        // The app's original classes are on its unit-test classpath as a jar, so the kill also shows the mutant wins.
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:mutkt")?.outcome, result.output)
        val app = dir.resolve("app/build/reports/mutkt/mutkt.txt").readText()
        assertTrue("Mutants: 1" in app && "KILLED       1" in app, app)
        val appJson = dir.resolve("app/build/reports/mutkt/mutation-report.json").readText()
        assertTrue("\"demo/app/Score.java\"" in appJson && "demo/lib" !in appJson, appJson)
        val lib = dir.resolve("lib/build/reports/mutkt/mutkt.txt").readText()
        assertTrue("Mutants: 1" in lib && "KILLED       1" in lib, lib)
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
    // AGP 9 gives unit tests only to the tested build type (8.4 has no such option): keep both, as every cell expects.
    private fun setUp(dsl: String) {
        write("settings.gradle", "rootProject.name = 'sample'\n")
        write("gradle.properties", "android.onlyEnableUnitTestForTheTestedBuildType=false\n")
        write("build.gradle", library(classpath = mutktClasspath() + "\n" + agpClasspath(), dsl = dsl))
    }

    private fun library(
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

    private fun mutktClasspath() =
        "classpath files(${MUTKT_CLASSPATH.joinToString { "'${it.invariantSeparatorsPath}'" }})"

    private fun agpClasspath() = "classpath 'com.android.tools.build:gradle:$AGP_VERSION'"

    private fun write(
        path: String,
        text: String,
    ) = dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun androidUserHome() = dir.resolve(".android").apply { resolve("cache").mkdirs() }

    private fun runner(task: String = "help") =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withTestKitDir(TEST_KIT_DIR)
            // AGP 8 creates its user home's cache in the first build, which the configuration cache sees as a change.
            .withEnvironment(System.getenv() + ("ANDROID_USER_HOME" to androidUserHome().absolutePath))
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

        const val REPOSITORIES = "repositories {\n    google()\n    mavenCentral()\n}"

        // Jupiter from this JVM's jars and the arithmetic operator only, so each module has one mutant.
        val MODULE_DSL =
            """
            $REPOSITORIES
            android { testOptions { unitTests.all { useJUnitPlatform() } } }
            dependencies { testImplementation files(${TestKitProject(File(".")).jars(JUNIT_JARS)}) }
            mutkt { operators = ['arithmetic'] }
            """.trimIndent()

        // A published AAR from google() (minSdk 19, so the app's default minSdk 1 would fail the manifest merge).
        val APP_DSL =
            """
            $MODULE_DSL
            android { defaultConfig { minSdk = 21 } }
            dependencies {
                implementation project(':lib')
                implementation 'androidx.annotation:annotation-experimental:1.4.1'
            }
            """.trimIndent()

        const val BASE =
            "package demo.lib;\npublic class Base {\n    public static int times10(int n) { return n * 10; }\n}\n"

        // Score calls the library module and a class from the AAR's classes.jar.
        val SCORE =
            """
            package demo.app;

            import androidx.annotation.RequiresOptIn;
            import demo.lib.Base;

            public class Score {
                public String score(int n) { return RequiresOptIn.Level.ERROR.name() + (Base.times10(1) + n); }
            }
            """.trimIndent()

        val SCORE_TEST =
            junitTest("demo.app", "ScoreTest", "Assertions.assertEquals(\"ERROR12\", new Score().score(2));")

        val BASE_TEST = junitTest("demo.lib", "BaseTest", "Assertions.assertEquals(20, Base.times10(2));")

        fun junitTest(
            pkg: String,
            name: String,
            assertion: String,
        ) = """
            |package $pkg;
            |
            |import org.junit.jupiter.api.Assertions;
            |import org.junit.jupiter.api.Test;
            |
            |public class $name {
            |    @Test
            |    void runs() {
            |        $assertion
            |    }
            |}
            """.trimMargin()

        // View binding generates demo.databinding.ActivityMainBinding, whose bind() and getRoot() have mutants.
        val BINDING_DSL =
            """
            repositories {
                google()
                mavenCentral()
            }
            android {
                buildFeatures { viewBinding = true }
                testOptions { unitTests.all { useJUnitPlatform() } }
            }
            dependencies { testImplementation files(${TestKitProject(File(".")).jars(JUNIT_JARS)}) }
            """.trimIndent()

        const val LAYOUT =
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" />\n"

        val GATE_TEST =
            """
            package demo;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;

            public class GateTest {
                @Test
                void adult() {
                    Assertions.assertTrue(new Gate().isAdult(30));
                }
            }
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

        // AGP 9 compiles Kotlin itself and rejects the Kotlin Android plugin; KGP on the classpath sets its version.
        val BUILT_IN_KOTLIN = AGP_VERSION.substringBefore('.').toInt() >= 9
        val GRADLE_VERSION: String =
            checkNotNull(System.getProperty("mutkt.agpGradleVersion")) { "mutkt.agpGradleVersion is not set" }

        val TEST_KIT_DIR = File(checkNotNull(System.getProperty("mutkt.testKitDir")) { "mutkt.testKitDir is not set" })

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
