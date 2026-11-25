plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    application
}

dependencies {
    // Only Mutator.scanMutations runs, so the jar's own runtime dependencies (JaCoCo, JUnit, asm-commons) are not needed.
    implementation("io.github.rodrigotimoteo:mutation-core:0.3.3") { isTransitive = false }
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation(libs.kctfork.core)
}

// kctfork 0.7.1 asks for a newer Kotlin than the one the fixtures are meant to be compiled with.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) useVersion(libs.versions.kotlin.get())
    }
}

application {
    mainClass = "ParityLegacyKt"
}
