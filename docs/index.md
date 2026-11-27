# MutKt

Mutation testing for Kotlin/JVM and Android. Add the Gradle plugin, run your existing JUnit tests against mutated bytecode, get a mutation score. No test changes.

Supported: JDK 17 and 21, Gradle 8.10 or later (including 9.x), and Android unit tests on AGP 8.4 through 9.x. Instrumented Android tests are not supported.

## Quick start

In `build.gradle.kts` of a project that applies `kotlin("jvm")`, `java` or an Android plugin and has JUnit 5 tests:

```kotlin
plugins {
    id("io.github.rodrigotimoteo.mutkt") version "<version>"
}
```

```bash
./gradlew mutkt
```

The score and the surviving mutants are printed in the build log. Reports land in `build/reports/mutkt/`: `mutation-report.html` (one offline page), `mutation-report.json` (Stryker schema) and `mutkt.txt` (the printed summary).

## Next

- [Getting started](getting-started.md): setup, reading the report, Android
- [Gradle DSL](dsl.md): every `mutkt { }` property
- [Operators](operators.md): what gets mutated
- [Migration from 0.3.x](migration.md)
- [API reference](https://rodrigotimoteo.github.io/mutkt/api/)
