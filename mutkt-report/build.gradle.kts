plugins {
    id("mutkt.published-library")
}

description = "Console, HTML, JSON and SARIF report writers of MutKt."

dependencies {
    implementation(project(":mutkt-core"))

    testImplementation(project(":mutkt-testing"))
    testImplementation(libs.jackson.databind)
}

tasks.test {
    val operatorsDoc = rootProject.layout.projectDirectory.file("docs/operators.md")
    inputs.file(operatorsDoc)
    systemProperty("mutkt.operatorsDoc", operatorsDoc.asFile.absolutePath)
}
