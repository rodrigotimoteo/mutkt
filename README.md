# MutKt

Mutation testing for Kotlin/JVM. Add the Gradle plugin, run your existing JUnit tests against mutated bytecode, get a mutation score. No test changes.

**Status:** 0.4.0, a full rebuild, is on Maven Central and the Gradle Plugin Portal. The previous implementation is tag `v0.3.3` (branch `old-master`); [docs/migration.md](docs/migration.md) lists what changed. See [getting started](docs/getting-started.md) to apply the plugin.

## Quick start

In `build.gradle.kts` of a project that applies `kotlin("jvm")` or `java` and has JUnit 5 tests:

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

- [Getting started](docs/getting-started.md)
- [DSL reference](docs/dsl.md)
- [Operators](docs/operators.md)
- [FAQ](docs/faq.md)
- [Migrating from 0.3.x](docs/migration.md)

Contributor and agent rules: [AGENTS.md](AGENTS.md). License: [LICENSE](LICENSE).
