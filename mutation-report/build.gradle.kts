plugins {
}

dependencies {
    implementation(project(":mutation-core"))
    testImplementation(project(":mutation-core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
