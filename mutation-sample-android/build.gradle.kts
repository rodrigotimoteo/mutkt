plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.3.0"
}

android {
    namespace = "com.github.rodrigotimoteo.mutation.sample.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.useJUnit()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.mockk:mockk-android:1.14.11")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

mutationTest {
    targetClasses.setFrom("com.github.rodrigotimoteo.mutation.sample.android")
    testClasses.setFrom("com.github.rodrigotimoteo.mutation.sample.android")
}
