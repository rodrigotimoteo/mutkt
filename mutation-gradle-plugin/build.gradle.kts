plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-test-runner"))

    // Gradle API
    implementation(gradleApi())

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict"
        ))
    }
}