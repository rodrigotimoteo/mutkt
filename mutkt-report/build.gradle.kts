plugins {
    id("mutkt.published-library")
}

description = "Console, HTML and JSON report writers of MutKt."

dependencies {
    implementation(project(":mutkt-core"))

    testImplementation(project(":mutkt-testing"))
    testImplementation(libs.jackson.databind)
}
