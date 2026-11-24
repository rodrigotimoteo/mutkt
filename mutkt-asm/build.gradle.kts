plugins {
    id("mutkt.published-library")
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(libs.asm)
}
