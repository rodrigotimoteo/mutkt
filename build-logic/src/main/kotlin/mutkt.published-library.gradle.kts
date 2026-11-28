import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("mutkt.kotlin-library")
    id("mutkt.publishing")
    id("org.jetbrains.kotlinx.kover")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()
    // The plugin runs on the Kotlin stdlib and compiler of the build's Gradle: 2.0 on Gradle 8.11, the minimum (ADR 0005).
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
    // Consumers resolve the stdlib these modules can call, not the newer one of the compiler.
    coreLibrariesVersion = libs.findVersion("kotlinStdlib").get().requiredVersion
}

kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
