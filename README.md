# Kotlin Mutation Testing

Free, plug-and-play mutation testing for Kotlin. Works with existing JUnit 5 tests - no test modifications required.

[![Maven Central](https://img.shields.io/maven-central/v/com.github.rodrigotimoteo/mutation-gradle-plugin)](https://central.sonatype.com/artifact/com.github.rodrigotimoteo/mutation-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Quick Start

1. Apply plugin:

```kotlin
plugins {
    id("com.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
}
```

2. Run:

```bash
./gradlew mutationTest
```

3. View report: `build/reports/mutation/index.html`

## Configuration (optional)

```kotlin
mutationTest {
    // Auto-detected from sourceSets, override if needed
    // targetClasses.from(files("build/classes/kotlin/main"))
    // testClasses.from(files("build/classes/kotlin/test"))

    timeoutMs.set(60000)  // 60 seconds per mutant
    maxParallelMutants.set(8)

    excludedClasses.addAll("com/example/Generated*", "com/example/BuildConfig")
}
```

## Mutation Operators

### Standard (7)

| Operator | Description |
|----------|-------------|
| `CONDITIONALS_BOUNDARY` | Mutates conditional boundaries: `>=` → `>`, `<=` → `<` |
| `NEGATE_CONDITIONALS` | Negates conditional operators: `==` → `!=`, `!=` → `==` |
| `ARITHMETIC` | Mutates arithmetic operators: `+` → `-`, `*` → `/` |
| `RETURN_VALS` | Replaces return values with constants |
| `NULL_RETURNS` | Replaces reference type returns with null |
| `EMPTY_RETURNS` | Replaces collection/array returns with empty instances |
| `INVERT_NEGS` | Inverts boolean negation: `!x` → `x` |

### Kotlin-Specific (4)

| Operator | Description |
|----------|-------------|
| `DATA_CLASS_COPY` | Mutates data class `copy()` parameters to default values |
| `SEALED_WHEN` | Removes or mutates sealed class `when` branches |
| `NULL_SAFETY` | Mutates null-safety operators: `?.let`, `?:`, `!!` |
| `COROUTINE` | Mutates coroutine builders and suspend functions |

## Features

- **Zero-config**: Just apply the plugin and run `./gradlew mutationTest`
- **Auto-discovery**: Automatically detects source sets, classpath, and JaCoCo coverage
- **Fast execution**: Parallel mutant testing with configurable concurrency
- **HTML reports**: Beautiful reports with mutation details
- **Kotlin-aware**: Specialized mutations for Kotlin-specific constructs

## How It Works

1. **Scans** your compiled bytecode for mutation points
2. **Generates** mutants by applying mutation operators
3. **Runs** your existing tests against each mutant
4. **Reports** which mutations survived (weak tests) and which were killed (strong tests)

## Requirements

- Kotlin 2.1.10+
- Gradle 8.10+
- JUnit 5 (for test execution)

## License

Apache License, Version 2.0 - see [LICENSE](LICENSE) for details.
