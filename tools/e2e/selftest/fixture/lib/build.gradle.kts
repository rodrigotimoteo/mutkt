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

tasks.test {
    useJUnitPlatform()
}
