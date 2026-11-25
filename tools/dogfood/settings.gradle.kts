pluginManagement {
    // The repository's own conventions, and the plugin under test built from the working tree.
    includeBuild("../../build-logic")
    includeBuild("../..")
    // The plugin's dependencies (ASM, JUnit Platform) and the conventions' Gradle plugins.
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Substitutes the repository's modules for the io.github.rodrigotimoteo coordinates used below.
includeBuild("../..")

rootProject.name = "dogfood"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include("core", "asm")
