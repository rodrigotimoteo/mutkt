import org.gradle.plugin.compatibility.compatibility

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
tasks.register<Test>("e2eTest") {
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("e2e") }
}

// Same scenarios as e2eTest on other Gradle versions; needs the network to fetch distributions, so it is not in check.
// The wrapper is 9.x, so e2eTest already covers it: the default adds the minimum supported Gradle and the latest 8.x.
tasks.register<Test>("matrixTest") {
    description =
        "Runs the TestKit scenarios on the Gradle versions of -Pmutkt.gradleVersions (default: minimum and latest 8.x)."
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("matrix") }
    // Read at configuration, so a new version list is a new task input rather than an up-to-date skip.
    systemProperty("mutkt.gradleVersions", providers.gradleProperty("mutkt.gradleVersions").getOrElse("8.11.1,8.14.3"))
}

// Android builds on real AGP; needs ANDROID_HOME and the network (google()), so it is not in check.
tasks.register<Test>("agpTest") {
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
    systemProperty("mutkt.kotlinVersion", libs.versions.kotlinAndroid.get())
    // MutKt's minimum Gradle (ADR 0005), which AGP 8.4 supports.
    systemProperty("mutkt.agpGradleVersion", providers.gradleProperty("mutkt.agpGradle").getOrElse("8.11.1"))
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
    dependsOn("e2eTest")
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
