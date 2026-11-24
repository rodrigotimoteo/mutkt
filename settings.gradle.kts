pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
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
