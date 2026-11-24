plugins {
    id("mutkt.published-library")
}

dependencies {
    testImplementation(libs.konsist)
}

// ArchitectureTest reads every module's sources and build files, which Gradle cannot see otherwise.
tasks.test {
    inputs.files(fileTree(rootDir) { include("mutkt-*/build.gradle.kts", "mutkt-*/src/main/**") })
}
