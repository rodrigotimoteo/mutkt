plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Like strikt's publishing script: configuration fails unless the corpus entry's gradleArgs reach Gradle.
val e2eSelftest = findProperty("e2eSelftest") as String

// Stands in for a Develocity scan upload, which the runner must catch in the log.
if (providers.gradleProperty("e2eScanLeak").isPresent) logger.lifecycle("Publishing build scan...\nhttps://gradle.com/s/e2eselftest")

tasks.test {
    useJUnitPlatform()
}
