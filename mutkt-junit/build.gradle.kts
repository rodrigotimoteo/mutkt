plugins {
    id("mutkt.published-library")
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.platform.launcher)
}
