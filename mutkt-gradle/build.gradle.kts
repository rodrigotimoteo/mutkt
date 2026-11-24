import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("mutkt.published-library")
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("mutkt") {
            id = "io.github.rodrigotimoteo.mutkt"
            implementationClass = "com.github.rodrigotimoteo.mutkt.gradle.MutKtPlugin"
        }
    }
}

// Gradle 8.10 Kotlin DSL embeds Kotlin 1.8 and cannot read newer API metadata of this plugin's classes.
kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_1_8
        apiVersion = KotlinVersion.KOTLIN_1_8
    }
}

tasks.test {
    val dslDoc = rootProject.layout.projectDirectory.file("docs/dsl.md")
    systemProperty("mutkt.dslDoc", dslDoc.asFile.absolutePath)
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(project(":mutkt-asm"))
    implementation(project(":mutkt-junit"))
    implementation(project(":mutkt-report"))
}
