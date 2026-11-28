package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.AGP_VERSION
import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.JUPITER_JARS
import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.KGP
import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.REPOSITORIES
import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.TEST_KIT_DIR
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.GATE
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.JUNIT4_GATE_TEST
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.JUNIT4_JARS
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.assertSummaryLines
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Android library builds on real AGP; run by `agpTest`, which needs an SDK (ANDROID_HOME) and the network. */
@Tag("agp")
class MutKtAndroidVariantTest {
    @TempDir
    lateinit var dir: File

    private val android get() = AndroidBuild(dir)

    @Test
    fun `the default debug variant passes configuration`() {
        android.setUp("")

        android.runner().build()
    }

    @Test
    fun `resolves AGP into the TestKit dir that CI caches`() {
        android.setUp("")

        android.runner().build()

        val agp = TEST_KIT_DIR.resolve("caches/modules-2/files-2.1/com.android.tools.build/gradle/$AGP_VERSION")
        assertTrue(agp.isDirectory, "$agp is missing")
    }

    @Test
    fun `another variant with unit tests passes configuration`() {
        android.setUp(
            """
            mutkt { variant = 'release' }
            androidComponents { beforeVariants(selector().withName('debug')) { it.enableUnitTest = false } }
            """.trimIndent(),
        )

        android.runner().build()
    }

    @Test
    fun `a missing variant fails with the variants that have unit tests`() {
        android.setUp("mutkt { variant = 'nope' }")

        val output = android.runner().buildAndFail().output

        assertTrue("MutKt cannot find variant 'nope'; variants with unit tests: debug, release" in output, output)
    }

    @Test
    fun `the default variant without unit tests fails with the variants that have them`() {
        android.setUp(
            "androidComponents { beforeVariants(selector().withName('debug')) { it.enableUnitTest = false } }",
        )

        val output = android.runner().buildAndFail().output

        assertTrue("MutKt variant 'debug' has no unit tests; variants with unit tests: release" in output, output)
    }

    @Test
    fun `mutates the variant's Kotlin and Java classes with its unit tests and reuses the configuration cache`() {
        android.write("settings.gradle", "rootProject.name = 'sample'\n")
        android.write(
            "build.gradle",
            android.library(
                classpath =
                    android.mutktClasspath() + "\n" + android.agpClasspath() + "\n" + KGP,
                dsl = RUN_DSL,
            ),
        )
        android.write(
            "src/main/kotlin/demo/Calc.kt",
            "package demo\n\nclass Calc {\n    fun add(a: Int, b: Int): Int = a + b\n}\n",
        )
        android.write("src/main/java/demo/Gate.java", GATE)
        android.write("src/test/java/demo/CalcTest.java", CALC_TEST)

        val first = android.runner("mutkt").build()
        val second = android.runner("mutkt").build()

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
    fun `JUnit 4 unit tests run on the vintage engine MutKt supplies`() {
        val junit4 = TestKitProject(File(".")).jars(JUNIT4_JARS)
        val dsl =
            "$REPOSITORIES\ndependencies { testImplementation files($junit4) }\n" +
                "mutkt { engines = ['junit-vintage']; operators = ['conditionals-boundary', 'negate-conditionals'] }"
        android.setUp(dsl)
        android.write("src/main/java/demo/Gate.java", GATE)
        android.write("src/test/java/demo/GateTest.java", JUNIT4_GATE_TEST)

        val result = android.runner("mutkt").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        val report = dir.resolve("build/reports/mutkt/mutkt.txt").readText()
        assertSummaryLines(report, "Mutants: 2", "KILLED       1", "SURVIVED     1")
    }

    @Test
    fun `Jupiter tests without a launcher, repositories only in settings, run on a launcher at the engine's version`() {
        val jupiter = "org.junit.jupiter:junit-jupiter:${System.getProperty("mutkt.junitOtherVersion")}"
        android.setUp(MODULE_DSL.replace(REPOSITORIES, "").replace(Regex("files\\(.*\\)"), "'$jupiter'"))
        val mode = "repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS"
        android.write(
            "settings.gradle",
            "rootProject.name = 'sample'\ndependencyResolutionManagement { $mode\n$REPOSITORIES }",
        )
        android.write("src/main/java/demo/Base.java", BASE.replace("demo.lib", "demo"))
        android.write("src/test/java/demo/BaseTest.java", BASE_TEST.replace("demo.lib", "demo"))

        val result = android.runner("mutkt").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        assertTrue("MutKt cannot resolve" !in result.output, result.output)
        assertSummaryLines(dir.resolve("build/reports/mutkt/mutkt.txt").readText(), "Mutants: 1", "KILLED       1")
    }

    @Test
    fun `skips generated classes by default, and excludedClasses extends or replaces the defaults`() {
        android.write("settings.gradle", "rootProject.name = 'sample'\n")
        android.write("gradle.properties", "android.useAndroidX=true\n")
        android.write("src/main/res/layout/activity_main.xml", LAYOUT)
        android.write("src/main/java/demo/Gate.java", GATE)
        android.write("src/main/java/demo/Old.java", GATE.replace("Gate", "Old"))
        android.write("src/test/java/demo/GateTest.java", GATE_TEST)
        val classpath = android.mutktClasspath() + "\n" + android.agpClasspath()
        val json = dir.resolve("build/reports/mutkt/mutation-report.json")

        android.write(
            "build.gradle",
            android.library(
                classpath,
                BINDING_DSL + "\nmutkt { excludedClasses.add('demo.Old') }",
            ),
        )
        android.runner("mutkt").build()
        val extended = json.readText()
        android.write("build.gradle", android.library(classpath, BINDING_DSL + "\nmutkt { excludedClasses = [] }"))
        android.runner("mutkt").build()
        val replaced = json.readText()

        assertTrue("\"demo/Gate.java\"" in extended, extended)
        assertTrue("ActivityMainBinding" !in extended && "\"demo/Old.java\"" !in extended, extended)
        assertTrue("ActivityMainBinding" in replaced && "\"demo/Old.java\"" in replaced, replaced)
    }

    @Test
    fun `an app mutant is killed by tests that need an Android library module and an AAR, and the library runs too`() {
        android.write("settings.gradle", "rootProject.name = 'sample'\ninclude 'app', 'lib'\n")
        android.write("gradle.properties", "android.useAndroidX=true\n")
        val plugins = "dependencies {\n${android.mutktClasspath()}\n${android.agpClasspath()}\n}"
        android.write("build.gradle", "buildscript {\n$REPOSITORIES\n$plugins\n}\n")
        android.write("lib/build.gradle", android.library("", MODULE_DSL, namespace = "demo.lib"))
        android.write("lib/src/main/java/demo/lib/Base.java", BASE)
        android.write("lib/src/test/java/demo/lib/BaseTest.java", BASE_TEST)
        android.write("app/build.gradle", android.library("", APP_DSL, "com.android.application", "demo.app"))
        android.write("app/src/main/AndroidManifest.xml", "<manifest />\n")
        android.write("app/src/main/java/demo/app/Score.java", SCORE)
        android.write("app/src/test/java/demo/app/ScoreTest.java", SCORE_TEST)

        val result = android.runner("mutkt").build()

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
        android.write("settings.gradle", "rootProject.name = 'sample'\ninclude 'lib'\n")
        android.write("build.gradle", "buildscript { dependencies { ${android.mutktClasspath()} } }\n")
        android.write("lib/build.gradle", android.library(classpath = android.agpClasspath(), dsl = ""))

        val output = android.runner().buildAndFail().output

        assertTrue("MutKt cannot load the Android Gradle plugin's classes" in output, output)
    }

    private companion object {
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
            dependencies { testImplementation files($JUPITER_JARS) }
            mutkt { operators = ['arithmetic', 'conditionals-boundary'] }
            """.trimIndent()

        // Jupiter from this JVM's jars and the arithmetic operator only, so each module has one mutant.
        val MODULE_DSL =
            """
            $REPOSITORIES
            android { testOptions { unitTests.all { useJUnitPlatform() } } }
            dependencies { testImplementation files($JUPITER_JARS) }
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
            dependencies { testImplementation files($JUPITER_JARS) }
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
    }
}
