plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
}

dependencies {
    // ASM for bytecode manipulation
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")
    implementation("org.ow2.asm:asm-analysis:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")

    // Kotlin reflection for metadata parsing
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")

    // Protobuf for Kotlin metadata decoding
    implementation("com.google.protobuf:protobuf-java:3.25.3")

    // JaCoCo for coverage analysis
    implementation("org.jacoco:org.jacoco.agent:0.8.12")
    implementation("org.jacoco:org.jacoco.core:0.8.12")
    implementation("org.jacoco:org.jacoco.report:0.8.12")

    // Kotlinx metadata library
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.7.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // JUnit (for test execution via reflection)
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict"
        ))
    }
}