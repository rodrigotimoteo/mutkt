plugins {
}

dependencies {
    // ASM for bytecode manipulation
    implementation(libs.asm.core)
    implementation(libs.asm.tree)
    implementation(libs.asm.commons)

    // Logging
    implementation(libs.slf4j.api)

    // JaCoCo for coverage-guided mutation testing
    implementation(libs.jacoco.core)

    // JUnit Platform Launcher API referenced from ReflectionTestRunner (compileOnly — required for compilation)
    compileOnly(libs.junit.platform.launcher)
    // JUnit Jupiter API (compileOnly — type references in code, e.g. annotations)
    compileOnly(libs.junit.jupiter)
    // JUnit 4 API (compileOnly — vintage engine references this at runtime)
    compileOnly(libs.junit4)
    // JUnit Vintage Engine — runs JUnit 4 tests via the JUnit Platform (runtimeOnly)
    runtimeOnly(libs.junit.vintage.engine)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit4)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
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
