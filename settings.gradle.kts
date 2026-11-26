pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // Only AGP's API, a compileOnly dependency of mutkt-gradle, comes from Google's repository.
        google {
            content { includeGroupByRegex("com\\.android\\..*") }
        }
    }
}

rootProject.name = "mutkt"

include(
    "mutkt-annotations",
    "mutkt-core",
    "mutkt-asm",
    "mutkt-junit",
    "mutkt-report",
    "mutkt-gradle",
    "mutkt-testing",
)
