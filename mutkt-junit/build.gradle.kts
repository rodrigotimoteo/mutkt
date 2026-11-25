plugins {
    id("mutkt.published-library")
}

description = "Isolated JUnit Platform test executor of MutKt."

dependencies {
    implementation(project(":mutkt-core"))
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.platform.launcher)

    testImplementation(project(":mutkt-testing"))
}
