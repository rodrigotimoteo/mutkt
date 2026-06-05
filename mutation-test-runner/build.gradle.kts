plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":mutation-core"))

    // JUnit Platform for test execution
    implementation(libs.junit.platform.launcher)
    implementation(libs.junit.jupiter)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":mutation-sample"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.asm.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjsr305=strict",
            ),
        )
    }
}
