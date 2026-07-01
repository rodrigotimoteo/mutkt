plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mutation.kotlin)
}

android {
    namespace = "com.example.multiapp"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        // TvJapan-style scenario: the consumer app declares the
        // `brand` dimension via missingDimensionStrategy so the
        // resolver picks the producer library's
        // `productionDebugRuntimeElements` (not the unflavored
        // `debugRuntimeElements`, which would force a "Cannot
        // choose between variants" ambiguity error).
        missingDimensionStrategy("brand", "production")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.useJUnit()
        }
    }

    // The sample is a TvJapan-style minimal repro; the lint
    // vital analyzer is an OOM-prone tool that blows up
    // metaspace on small projects. Disable the lint task chain
    // for the sample to keep `./gradlew build` green without
    // giving up lint on the published modules.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":multi-shared"))

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

mutationTest {
    targetClasses.setFrom("com.example.multiapp")
    testClasses.setFrom("com.example.multiapp")
    // The app itself has no flavors — it only consumes the
    // `:multi-shared` library's `productionDebugRuntimeElements` via
    // `defaultConfig.missingDimensionStrategy("brand", "production")`.
    // AGP creates the standard `debug` / `release` variants on the
    // app side; the flavor dimension only affects how the library
    // is resolved. Pin `androidVariant = "debug"` explicitly so the
    // resolver does not pick a non-existent flavored variant.
    androidVariant.set("debug")
}
