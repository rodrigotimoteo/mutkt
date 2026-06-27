plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    // Required for AGP compileOnly dependency (com.android.tools.build:gradle).
    google()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("mutation-kotlin") {
            id = "io.github.rodrigotimoteo.mutation-kotlin"
            implementationClass = "com.github.rodrigotimoteo.mutation.gradle.MutationPlugin"
            displayName = "MutKt - Kotlin Mutation Testing"
            description = "PITest-style mutation testing for Kotlin/JVM. Run existing tests against mutated bytecode."
            tags.set(listOf("mutation-testing", "kotlin", "testing", "code-quality"))
            website.set("https://github.com/rodrigotimoteo/mutkt")
            vcsUrl.set("https://github.com/rodrigotimoteo/mutkt")
        }
    }
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-report"))
    implementation(project(":mutation-test-runner"))

    // Gradle API
    implementation(gradleApi())

    // Android Gradle Plugin (compileOnly — only used for plugin type detection).
    // Also on the test classpath so unit tests can apply the plugin without
    // NoClassDefFoundError when Gradle inspects the AGP-referencing callbacks.
    compileOnly(libs.agp)
    testImplementation(libs.agp)

    // Kotlin Gradle Plugin (compileOnly — used to discover KotlinCompile tasks
    // on the consuming project's classpath via project.tasks.withType).
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotlin.gradle.plugin)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.asm.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjsr305=strict",
            ),
        )
    }
}
