plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("mutation-kotlin") {
            id = "io.github.rodrigotimoteo.mutation-kotlin"
            implementationClass = "com.github.rodrigotimoteo.mutation.gradle.MutationPlugin"
        }
    }
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-report"))
    implementation(project(":mutation-test-runner"))

    // Gradle API
    implementation(gradleApi())

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.asm.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjsr305=strict",
            ),
        )
    }
}
