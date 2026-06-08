plugins {
}

dependencies {
    // ASM for bytecode manipulation
    implementation(libs.asm.core)
    implementation(libs.asm.tree)
    implementation(libs.asm.commons)

    // Logging
    implementation(libs.slf4j.api)

    // JUnit for test execution via reflection
    implementation(libs.junit.jupiter)
    implementation("org.junit.jupiter:junit-jupiter-params:6.1.0")

    // JUnit Platform Launcher for native JUnit 5 support
    implementation(libs.junit.platform.launcher)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
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
