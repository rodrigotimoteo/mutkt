pluginManagement {
    repositories {
        // The MutKt plugin, published from this repository's working tree (see README.md).
        maven { url = uri("../../build/sample-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
            // CI runs the sample on the minimum and the latest AGP: -Pmutkt.agp=9.5.3.
            providers.gradleProperty("mutkt.agp").orNull?.let { version("agp", it) }
        }
    }
}

rootProject.name = "mutkt-android-sample"
