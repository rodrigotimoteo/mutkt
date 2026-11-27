# MutKt

Mutation testing for Kotlin/JVM and Android. Add the Gradle plugin, run your existing JUnit tests against mutated bytecode, get a mutation score. No test changes.

**Status:** 0.5.0, a full rebuild with Android support, is on Maven Central and the Gradle Plugin Portal. The previous implementation is tag `v0.3.3` (branch `old-master`); [docs/migration.md](docs/migration.md) lists what changed. See [getting started](docs/getting-started.md) to apply the plugin.

## Quick start

Supported: JDK 17 and 21, Gradle 8.11 or later (including 9.x), and Android unit tests on AGP 8.4 through 9.x ([Android setup](docs/getting-started.md#android)). Instrumented Android tests are not supported.

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

## Docs

The guides and the API reference are published at <https://rodrigotimoteo.github.io/mutkt/>.

- [Getting started](docs/getting-started.md)
- [DSL reference](docs/dsl.md)
- [Operators](docs/operators.md)
- [FAQ](docs/faq.md)
- [Migrating from 0.3.x](docs/migration.md)
- [API reference](https://rodrigotimoteo.github.io/mutkt/api/)

Contributor and agent rules: [AGENTS.md](AGENTS.md). License: [LICENSE](LICENSE).
