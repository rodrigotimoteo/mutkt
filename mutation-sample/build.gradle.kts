plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-report"))
    implementation(project(":mutation-test-runner"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-report"))
    implementation(project(":mutation-test-runner"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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
