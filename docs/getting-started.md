# Getting started

## Requirements

- JDK 17 or 21, Gradle 8.10 or a later 8.x.
- The `java` or `kotlin("jvm")` plugin; MutKt fails the build without one. Android is not supported.
- Tests on the JUnit Platform. Jupiter runs by default; JUnit 4 tests need `engines` (see [dsl.md](dsl.md)).
- A green test suite: MutKt runs it once on the original classes first and stops if anything fails.

## Apply the plugin

From the Gradle Plugin Portal (the latest version is in the [CHANGELOG](../CHANGELOG.md)):

```kotlin
plugins {
    kotlin("jvm") version "<kotlin version>"
    id("io.github.rodrigotimoteo.mutkt") version "<version>"
}
```

To try an unreleased change, clone this repository and include it in `settings.gradle.kts` (the setup [dogfood.md](dogfood.md) uses):

```kotlin
pluginManagement {
    includeBuild("../mutkt")
}
// Only needed for @SuppressMutations: resolves io.github.rodrigotimoteo:mutkt-annotations to the clone.
includeBuild("../mutkt")
```

Then apply `id("io.github.rodrigotimoteo.mutkt")` without a version.

Recommended: put the project's own JUnit Platform launcher on the test runtime classpath, so the mutants run on the same Platform version as your tests:

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:<junit version>"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

Without it the mutants run on the Platform bundled with MutKt (1.10.2), which may be older than your engine.

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
