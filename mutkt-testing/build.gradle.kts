plugins {
    id("mutkt.kotlin-library")
}

dependencies {
    api(project(":mutkt-core"))
    api(libs.asm)
    implementation(libs.kctfork.core)
    implementation(libs.kotlin.compose.compiler)
    // Fixtures only compile against the runtime; its transitive androidx artifacts live outside Maven Central.
    implementation(libs.compose.runtime) { isTransitive = false }
}
