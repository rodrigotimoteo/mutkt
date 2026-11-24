plugins {
    id("mutkt.published-library")
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(project(":mutkt-asm"))
    implementation(project(":mutkt-junit"))
    implementation(project(":mutkt-report"))
}
