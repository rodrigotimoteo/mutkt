plugins {
    id("mutkt.published-library")
}

description = "Bytecode mutation operators and mutant generator of MutKt, built on ASM."

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    testImplementation(project(":mutkt-testing"))
    testImplementation(libs.asm.util)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    val parityDoc = rootProject.layout.projectDirectory.file("docs/parity.md")
    inputs.file(parityDoc)
    systemProperty("mutkt.parityDoc", parityDoc.asFile.absolutePath)
    val operatorsDoc = rootProject.layout.projectDirectory.file("docs/operators.md")
    inputs.file(operatorsDoc)
    systemProperty("mutkt.operatorsDoc", operatorsDoc.asFile.absolutePath)
}
