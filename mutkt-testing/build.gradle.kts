plugins {
    id("mutkt.kotlin-library")
}

dependencies {
    implementation(project(":mutkt-core"))
    api(libs.asm)
    implementation(libs.kctfork.core)
}
