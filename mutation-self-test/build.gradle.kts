plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":mutation-core"))
    implementation(project(":mutation-test-runner"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation(kotlin("test"))
    testImplementation("org.ow2.asm:asm:9.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
