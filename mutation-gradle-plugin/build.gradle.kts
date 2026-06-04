plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-test-runner"))

    // Gradle API
    implementation(gradleApi())

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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
