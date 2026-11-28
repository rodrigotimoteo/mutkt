# Getting started

## Requirements

- JDK 17 or 21, Gradle 8.10 or later, including 9.x.
- The `java`, `kotlin("jvm")` or an Android plugin (AGP 8.4 to 9.x, see [Android](#android)); MutKt fails the build without one.
- Tests on the JUnit Platform. Jupiter runs by default; JUnit 4 tests need `engines` (see [dsl.md](dsl.md)).
- A green test suite: MutKt runs it once on the original classes first and stops if anything fails.

## Apply the plugin

From the Gradle Plugin Portal (the latest version is in the [changelog](changelog.md)):

```kotlin
plugins {
    kotlin("jvm") version "<kotlin version>"
    id("io.github.rodrigotimoteo.mutkt") version "<version>"
}
```

To try an unreleased change, clone this repository and include it in `settings.gradle.kts` (the setup [dogfood.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/dogfood.md) uses):

```kotlin
pluginManagement {
    includeBuild("../mutkt")
}
// Only needed for @SuppressMutations: resolves io.github.rodrigotimoteo:mutkt-annotations to the clone.
includeBuild("../mutkt")
```

Then apply `id("io.github.rodrigotimoteo.mutkt")` without a version.

The mutants run on your project's JUnit Platform. Gradle 9 adds no `junit-platform-launcher` to the test runtime classpath and Gradle 8 adds it to the `test` task only, so when it is missing, `mutkt` resolves it from your repositories at the version of your `junit-platform-engine`. Declaring it skips that lookup:

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:<junit version>"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

If the launcher cannot be resolved (offline, for instance), the mutants run on the Platform bundled with MutKt (1.10.2), which may be older than your engine, and the build logs a warning naming the dependency to add.

## Run

```bash
./gradlew mutkt
```

MutKt mutates the `main` classes with the six default operators and runs the `test` classes against each mutant, in child JVMs set up like the `test` task. The build log shows the counts per status, the score, a table per class and every survivor with its source line:

```text
Mutation report
Mutants: 16
  KILLED       13
  SURVIVED     2
  TIMEOUT      1
  NO_COVERAGE  0
  ERROR        0
Killed 13 (assertions 11, exceptions 2)
Score: 87.5%
```

Score = (KILLED + TIMEOUT) / (all mutants − ERROR). A survivor is a change to your code that no test noticed: add or tighten a test, or, if the change cannot alter behavior, suppress it (see [faq.md](faq.md#equivalent-mutants)).

## Reports

All in `build/reports/mutkt/`, rewritten on every run (a run that fails before scoring, such as on a red test suite, leaves none):

| File | Content |
|---|---|
| `mutation-report.html` | One offline page: source with each mutant and its status |
| `mutation-report.json` | Stryker mutation-testing-elements schema 2, sources included |
| `mutkt.txt` | The summary printed in the build log |

The task is up to date until classes, sources, the test classpath or the settings change; an up-to-date run prints nothing.

## Android

MutKt runs on Android application and library modules (`com.android.application`, `com.android.library`) with AGP 8.4 through 9.x and Gradle 8.10 or later, including 9.x (AGP 9 itself needs Gradle 9.1 or later). CI tests AGP 8.4.2, 8.13.2 and 9.5.3.

It mutates the classes of one variant (Kotlin and Java, as AGP compiled them) and runs that variant's local unit tests (`src/test`) against each mutant. The test classes, classpath (Android library modules and AAR dependencies included) and JVM settings (`unitTests.all { }`) come from the variant's unit-test task, `test<Variant>UnitTest`; MutKt reads them without running that task.

AGP 8.x, with the Kotlin Android plugin (`build.gradle.kts` of the module):

```kotlin
plugins {
    id("com.android.library") version "<AGP 8 version>"
    id("org.jetbrains.kotlin.android") version "<kotlin version>"
    id("io.github.rodrigotimoteo.mutkt") version "<version>"
}

android {
    namespace = "com.example.lib"
    compileSdk = 34
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:<junit version>"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mutkt {
    variant = "debug"
}
```

AGP 9.x compiles Kotlin itself and rejects `org.jetbrains.kotlin.android`, so the plugins block drops it; the rest is the same:

```kotlin
plugins {
    id("com.android.library") version "<AGP 9 version>"
    id("io.github.rodrigotimoteo.mutkt") version "<version>"
}
```

`com.android.application` works the same way. `variant` defaults to `debug`; a flavored build names the full variant, such as `freeDebug`. A variant that does not exist or has no unit tests fails the build with the names of the variants that have them. On AGP 9 only the tested build type (`debug`) has unit tests: to use `variant = "release"`, set `android.onlyEnableUnitTestForTheTestedBuildType=false` in `gradle.properties`.

MutKt must see AGP's classes: declare the Android plugin in the same `plugins { }` block as MutKt, as above, or in a parent project's. In a multi-module build, declare both in the root with `apply false` and apply them in the modules:

```kotlin
// Root build.gradle.kts
plugins {
    id("com.android.application") version "<AGP version>" apply false
    id("io.github.rodrigotimoteo.mutkt") version "<version>" apply false
}
```

Each module that applies MutKt gets its own `mutkt` task, which mutates only that module's classes and writes its reports to the module's `build/reports/mutkt/` (`app/build/reports/mutkt/` for an `app` module). `./gradlew mutkt` runs every module's.

Classes the Android build generates (R, BuildConfig, Hilt and Dagger, view and data binding, Compose singletons) are excluded by default; [dsl.md](dsl.md#android-generated-classes) lists the globs and how to extend or replace them.

**JUnit 4 and Robolectric.** JUnit 4 tests, Robolectric's included, run on the vintage engine, so they need `engines`. MutKt adds the vintage engine to its test JVMs when the project has none, so the unit-test task can stay on JUnit 4:

```kotlin
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:<junit 4 version>")
    testImplementation("org.robolectric:robolectric:<robolectric version>")
}

mutkt {
    engines = setOf("junit-vintage")
}
```

The mutants then run with the unit-test task's classpath, which holds Robolectric's resource config when `isIncludeAndroidResources` is on. The [Android sample](https://github.com/rodrigotimoteo/mutkt/tree/master/samples/android) is a working build with Robolectric tests on AGP 8 and 9.

**Not supported:** instrumented tests (`src/androidTest`, `connectedAndroidTest`, devices and emulators), more than one variant per run, test fixtures, screenshot tests, and `com.android.dynamic-feature`, `com.android.test` and Kotlin Multiplatform Android modules.

## Configure

```kotlin
mutkt {
    targetClasses.add("com.example.**")
    excludedClasses.add("com.example.generated.**")
    failBelowScore = 80
}
```

Every property is in [dsl.md](dsl.md); the operators and their ids are in [operators.md](operators.md).

To run it with `check`:

```kotlin
tasks.check { dependsOn("mutkt") }
```
