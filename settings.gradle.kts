pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kotlin-mutation"

include("mutation-core")
include("mutation-report")
include("mutation-test-runner")
include("mutation-gradle-plugin")
include("mutation-sample")
include("mutation-sample-android")
include("mutation-self-test")
