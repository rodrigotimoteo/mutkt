plugins {
    id("mutkt.kotlin-library")
    id("io.github.rodrigotimoteo.mutkt")
}

// The module's own dependencies; the mutkt-asm artifact itself cannot be used, it would shadow the compiled classes.
dependencies {
    implementation("io.github.rodrigotimoteo:mutkt-core")
    implementation("io.github.rodrigotimoteo:mutkt-annotations")
    implementation(libs.asm)
    implementation(libs.asm.tree)
    testImplementation("io.github.rodrigotimoteo:mutkt-testing")
    testImplementation(libs.asm.util)
    testImplementation(libs.kotlinx.coroutines.core)
}

mutkt {
    targetClasses.add("com.github.rodrigotimoteo.mutkt.asm.operators.**")
    // The whole asm suite compiles Kotlin fixtures, so one run per mutant takes far longer than the 10 s default.
    timeoutMs = 120_000
    // The first mutants of each operator class keep the job to minutes; the cap is part of the ratchet.
    maxMutantsPerClass = 3
    failBelowScore = 90
}
