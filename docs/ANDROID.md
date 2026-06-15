# MutKt for Android

## Quick start (5 min)

### Prerequisites

- Android Gradle Plugin 8.0+ (older versions not supported)
- Kotlin 2.1.0+
- Robolectric 4.13+ (required for unit tests that use Android framework)

### Setup

In your `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    repositories {
        mavenLocal()  // for local dev, or remove for Maven Central
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.github.rodrigotimoteo:mutation-gradle-plugin:0.3.0")
    }
}

apply(plugin = "io.github.rodrigotimoteo.mutation-kotlin")

android {
    // ... your existing config ...
    testOptions {
        unitTests.isIncludeAndroidResources = true  // required for Robolectric
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

mutationTest {
    targetClasses.setFrom("com.example.app.*")
    testClasses.setFrom("com.example.app.*Test")
    // Optional: pick a specific variant (default: debug)
    androidVariant.set("debug")
}
```

Run:

```bash
./gradlew mutationTest
```

Report at `app/build/reports/mutation/mutation-report.html`.

## How it works

When MutKt detects an Android plugin (`com.android.application` or `com.android.library`):

1. **Variant resolution** — queries AGP for the runtime classpath, classes dirs, test classes dirs, and the matching `android.jar` from your SDK install
2. **AAR extraction** — extracts `classes.jar` from any `.aar` dependencies in your runtime classpath
3. **Generated class filtering** — excludes framework-generated classes from mutation: `R`, `R$*`, `BuildConfig`, `ComposableSingletons$*`, `*_Impl` (Room), `*_Factory` (Dagger), `*Hilt*`, etc.
4. **Compile task wiring** — automatically depends on the variant's `compile<Variant>Kotlin` and `compile<Variant>UnitTestKotlin` tasks
5. **Robolectric execution** — runs your unit tests in a JVM with shadowed Android framework, so they can load `android.*` classes without a device

## Configuration

### DSL properties (Android-specific)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `isAndroid` | `Property<Boolean>` | auto-detected | Set to true if MutKt failed to detect AGP |
| `androidPluginType` | `Property<String>` | auto-detected | "application" or "library" |
| `androidVariant` | `Property<String>` | "debug" | Which build variant to test |
| `excludeGeneratedClasses` | `SetProperty<String>` | 20 patterns | Classes to skip during mutation (R, BuildConfig, etc.) |

### Customizing generated class patterns

```kotlin
mutationTest {
    excludeGeneratedClasses.set(setOf(
        "R", "R$*", "BuildConfig",
        "*_Impl",  // Room
        "*_Factory",  // Dagger
        "*MyCustom*"
    ))
}
```

## Known limitations

- **Instrumented tests not supported** — MutKt only runs JVM unit tests. Instrumented tests (on device/emulator) are out of scope.
- **No Hilt test execution** — Hilt's test rules use a custom test runner that doesn't work with MutKt's reflection-based runner. Use Robolectric for now.
- **Compose runtime classes skipped** — Composable functions, lambdas, and `@ComposableSingletons$*` are excluded from mutation.
- **AGP 7.x not supported** — Requires AGP 8.0+ for the modern `AndroidComponentsExtension` API.

## Troubleshooting

### `NoClassDefFoundError: android/os/Parcelable`

Your test classpath is missing `android.jar`. MutKt should auto-detect this from `$ANDROID_HOME/platforms/`. Verify:

```bash
echo $ANDROID_HOME
ls $ANDROID_HOME/platforms/
```

If missing, set `ANDROID_HOME` in `local.properties`:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

### `Task with name 'compileKotlin' not found`

This happens on Android because AGP uses variant-specific task names (`compileDebugKotlin`, `compileReleaseKotlin`). MutKt should auto-detect this. If it fails, set manually:

```kotlin
mutationTest {
    compileTask.set("compileDebugKotlin")
    testCompileTask.set("compileDebugUnitTestKotlin")
}
```

### Mutations all show as `NO_COVERAGE`

The reflection-based test runner isn't executing your `@Test` methods. Common causes:

1. Tests use Hilt's `@HiltAndroidTest` — not supported, use Robolectric instead
2. Tests use JUnit 5 (`@Test` from `org.junit.jupiter.api`) — MutKt supports both, but verify
3. Test class doesn't follow `*Test` / `*Tests` naming pattern

### `AGP attribute conflict` errors

Your AAR dependencies don't have matching variant attributes. This is rare. Workaround: add the AAR's `classes.jar` to your classpath manually.

## Example

See `mutation-sample-android/` in the MutKt project for a working example with Robolectric.
