plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mutation.kotlin)
}

android {
    namespace = "com.example.multishared"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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

    // TvJapan-style scenario: the library publishes multiple
    // product-flavor variants. The app must declare a
    // `missingDimensionStrategy("brand", "production")` (or its
    // own `brand` flavor) so Gradle can pick a producer variant.
    flavorDimensions += "brand"
    productFlavors {
        create("production") {
            dimension = "brand"
        }
        create("staging") {
            dimension = "brand"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.useJUnit()
        }
    }

    // Mirror the lint guard from :multi-app: the lint vital
    // analyzer OOMs in Metaspace on small sample modules.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

mutationTest {
    targetClasses.setFrom("com.example.multishared")
    testClasses.setFrom("com.example.multishared")
    androidVariant.set("productionDebug")
}
