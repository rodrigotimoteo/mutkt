plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android) apply false
    id("io.github.rodrigotimoteo.mutkt") version "0.0.0-sample"
}

// AGP 9 compiles Kotlin itself and rejects the Kotlin Android plugin.
if (libs.versions.agp.get().substringBefore('.').toInt() < 9) {
    apply(plugin = "org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mutkt.android"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // Robolectric reads the merged resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mutkt {
    // Robolectric runs on JUnit 4, so the tests run through the vintage engine.
    engines = setOf("junit-vintage")
}
