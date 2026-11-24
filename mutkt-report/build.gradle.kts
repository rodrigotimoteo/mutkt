plugins {
    id("mutkt.published-library")
}

dependencies {
    implementation(project(":mutkt-core"))

    testImplementation(project(":mutkt-testing"))
}
