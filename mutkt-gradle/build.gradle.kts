import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("mutkt.published-library")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
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
    useJUnitPlatform { excludeTags("e2e") }
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
    val versions = providers.gradleProperty("mutkt.gradleVersions").orElse("8.14.3")
    // Test.systemProperty does not resolve providers, so the value is read when the JVM arguments are built.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dmutkt.gradleVersions=${versions.get()}") })
}

tasks.check {
    dependsOn(e2eTest)
}

// Kover would otherwise run every Test task for the coverage report, pulling this network-bound one into build.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("matrixTest")
        }
    }
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(project(":mutkt-asm"))
    implementation(project(":mutkt-junit"))
    implementation(project(":mutkt-report"))

    testImplementation(project(":mutkt-testing"))
    testImplementation(gradleTestKit())
}
