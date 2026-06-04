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

    // Testing
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
