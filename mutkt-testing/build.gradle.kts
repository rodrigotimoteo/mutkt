plugins {
    id("mutkt.kotlin-library")
}

dependencies {
    api(project(":mutkt-core"))
    api(libs.asm)
    implementation(libs.kctfork.core)
}
