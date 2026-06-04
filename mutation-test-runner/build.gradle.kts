plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
}

dependencies {
    implementation(project(":mutation-core"))

    // JUnit Platform for test execution
    implementation("org.junit.platform:junit-platform-launcher:1.10.2")
    implementation("org.junit.platform:junit-platform-engine:1.10.2")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // Kotlin test support
    implementation("org.jetbrains.kotlin:kotlin-test-junit5:2.1.10")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
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