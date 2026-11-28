import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("mutkt.published-library")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
    id("mutkt.dokka")
}

description = "Gradle plugin that runs mutation testing on the compiled bytecode of a Kotlin/JVM build."

gradlePlugin {
    website = "https://github.com/rodrigotimoteo/mutkt"
    vcsUrl = "https://github.com/rodrigotimoteo/mutkt.git"
    plugins {
        create("mutkt") {
            id = "io.github.rodrigotimoteo.mutkt"
            implementationClass = "com.github.rodrigotimoteo.mutkt.gradle.MutKtPlugin"
            displayName = "MutKt mutation testing"
            description = "Mutates compiled Kotlin bytecode and runs your existing JUnit tests against each mutant."
            tags = listOf("mutation-testing", "kotlin", "testing", "junit")
            // Every TestKit scenario runs with --configuration-cache.
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

// Gradle 8.10 Kotlin DSL embeds Kotlin 1.8 and cannot read newer API metadata of this plugin's classes.
kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_1_8
        apiVersion = KotlinVersion.KOTLIN_1_8
    }
}

tasks.withType<Test>().configureEach {
    val dslDoc = rootProject.layout.projectDirectory.file("docs/dsl.md")
    systemProperty("mutkt.dslDoc", dslDoc.asFile.absolutePath)
    val readme = rootProject.layout.projectDirectory.file("README.md")
    systemProperty("mutkt.readme", readme.asFile.absolutePath)
    // Absolute paths in system properties are not tracked, so a docs-only change would leave the tests up to date.
    inputs
        .files(readme, rootProject.layout.projectDirectory.dir("docs"))
        .withPropertyName("userDocs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The end-to-end golden file is rewritten with -Dmutkt.updateGolden=true.
    val goldenDir = layout.projectDirectory.dir("src/test/resources/e2e")
    systemProperty("mutkt.goldenDir", goldenDir.asFile.absolutePath)
    providers.systemProperty("mutkt.updateGolden").orNull?.let { systemProperty("mutkt.updateGolden", it) }
}

tasks.test {
    useJUnitPlatform { excludeTags("e2e", "agp") }
}

// Own JVM: the end-to-end timeout mutant leaves a spinning thread that must not outlive its test JVM's other tests.
val e2eTest by tasks.registering(Test::class) {
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("e2e") }
}

// Same scenarios as e2eTest on other Gradle versions; needs the network to fetch distributions, so it is not in check.
// The minimum supported Gradle is 8.10 (Kotlin DSL limit above) and the wrapper is 8.10, so e2eTest already covers it:
// the default adds only the latest 8.x.
val matrixTest by tasks.registering(Test::class) {
    description =
        "Runs the TestKit scenarios on the Gradle versions of -Pmutkt.gradleVersions (minimum and latest 8.x)."
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("matrix") }
    // Read at configuration, so a new version list is a new task input rather than an up-to-date skip.
    systemProperty("mutkt.gradleVersions", providers.gradleProperty("mutkt.gradleVersions").getOrElse("8.14.3"))
}

// Android builds on real AGP; needs ANDROID_HOME and the network (google()), so it is not in check.
val agpTest by tasks.registering(Test::class) {
    description = "Runs the Android TestKit scenarios on -Pmutkt.agp and -Pmutkt.agpGradle (default: minimum AGP)."
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("agp") }
    // Read at configuration, so a new cell is a new task input rather than an up-to-date skip.
    systemProperty("mutkt.agpVersion", providers.gradleProperty("mutkt.agp").getOrElse(libs.versions.agp.get()))
    // A Jupiter newer than the bundled Platform, for the scenario without a launcher.
    systemProperty("mutkt.junitOtherVersion", libs.versions.junitOther.get())
    // The Kotlin Android plugin of the scenario that mutates a Kotlin class.
    systemProperty("mutkt.kotlinVersion", libs.versions.kotlin.get())
    // AGP 8.4's minimum Gradle.
    systemProperty("mutkt.agpGradleVersion", providers.gradleProperty("mutkt.agpGradle").getOrElse("8.6"))
    // A fixed Gradle user home for the scenarios, so CI can cache what they download (AGP, Kotlin) between runs.
    val testKitDir =
        layout.buildDirectory
            .dir("testkit")
            .get()
            .asFile
    systemProperty("mutkt.testKitDir", testKitDir.absolutePath)
    // Run only on request, so a missing SDK is an error rather than a silent pass.
    val androidHome = providers.environmentVariable("ANDROID_HOME")
    doFirst {
        if (!androidHome.isPresent) throw GradleException("agpTest needs an Android SDK: set ANDROID_HOME")
    }
}

tasks.check {
    dependsOn(e2eTest)
}

// Kover would otherwise run every Test task for the coverage report, pulling this network-bound one into build.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("matrixTest", "agpTest")
        }
    }
    reports {
        filters {
            excludes {
                // Covered by agpTest, which needs an Android SDK and runs in its own CI job (ROD-217).
                classes("com.github.rodrigotimoteo.mutkt.gradle.MutKtAndroid*")
            }
        }
    }
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(project(":mutkt-asm"))
    implementation(project(":mutkt-junit"))
    implementation(project(":mutkt-report"))
    compileOnly(libs.agp.api)

    testImplementation(project(":mutkt-testing"))
    testImplementation(gradleTestKit())
}
