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

    // Testing. The previous `testImplementation(project(":mutation-sample"))`
    // closed a build cycle: mutation-sample depends on this module, and
    // this module's tests depended on mutation-sample. The integration
    // tests load Calculator.class directly from the filesystem (see
    // `loadCalculatorClassBytes` in MutatorDebugTest / AsmDebugTest and
    // `findClassesDir` in MutationEngineIntegrationTest) and only need
    // `mutation-sample` to be built before this module's tests run, not
    // linked as a Gradle dependency. The cycle is broken here and the
    // build contract is preserved by the README's
    // `./gradlew :mutation-sample:build` prerequisite.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.asm.core)
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
