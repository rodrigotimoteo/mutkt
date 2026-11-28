plugins {
    id("mutkt.published-library")
}

description = "Isolated JUnit Platform test executor of MutKt."

// Jupiter and Platform of a second version line, as a project on other JUnit than the bundled one would have them.
val otherPlatform =
    configurations.create("otherPlatform") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation(project(":mutkt-core"))
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.platform.launcher)
    // Supplied to JUnit 4 projects that select the vintage engine but have none on their test runtime classpath.
    runtimeOnly(libs.junit.vintage.engine)

    testImplementation(project(":mutkt-testing"))

    otherPlatform(platform(libs.junit.bom.other))
    otherPlatform(libs.junit.jupiter)
    otherPlatform(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("platformCompat") }
}

// Kover would otherwise run every Test task for the coverage report, pulling this network-bound one into build.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("platformCompatTest")
        }
    }
}

// Needs the network to resolve the second version line, so it is not in check; CI runs it as its own job.
tasks.register<Test>("platformCompatTest") {
    description = "Runs the forked executor on a second JUnit Platform version (libs.versions.junitOther)."
    group = "verification"
    val testSources = sourceSets.test.get()
    testClassesDirs = testSources.output.classesDirs
    classpath = testSources.runtimeClasspath
    useJUnitPlatform { includeTags("platformCompat") }
    inputs.files(otherPlatform)
    val jars = otherPlatform.elements.map { files -> files.joinToString(File.pathSeparator) { it.asFile.path } }
    // Test.systemProperty does not resolve providers, so the value is read when the JVM arguments are built.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dmutkt.otherPlatform=${jars.get()}") })
}
