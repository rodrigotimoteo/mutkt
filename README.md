# MutKt

**Kotlin Mutation Testing — PITest-style for Kotlin/JVM**

[![CI](https://github.com/rodrigotimoteo/mutkt/actions/workflows/ci.yml/badge.svg)](https://github.com/rodrigotimoteo/mutkt/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.rodrigotimoteo/mutation-core)](https://central.sonatype.com/search?q=g:io.github.rodrigotimoteo)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

MutKt is a mutation testing library for Kotlin and Java that runs your existing test suite against mutated versions of your code. It finds gaps in your tests by verifying they actually catch bugs — not just pass.

## Quick Start

### Gradle Plugin (Recommended)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.10"
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.2.0"
}
```

Run:
```bash
./gradlew mutationTest
```

That's it. Zero configuration required. The plugin auto-detects:
- Source sets (`src/main/kotlin`, `src/main/java`)
- Classpath (test runtime dependencies)
- Kotlin vs Java compilation

### JUnit Extension

For explicit control over which code blocks are mutated:

```kotlin
dependencies {
    testImplementation("io.github.rodrigotimoteo:mutation-test-runner:0.2.0")
}

@ExtendWith(MutKtExtension::class)
@MutKtTest
class CalculatorTest {
    @Test
    fun testAdd() = MutKt.underTest {
        val calc = Calculator()
        assertEquals(4, calc.add(2, 2))
    }
}
```

## How It Works

1. **Scan** — MutKt scans compiled bytecode for mutation points
2. **Mutate** — Each mutation point gets one mutant (e.g., `+` → `-`)
3. **Execute** — Your test suite runs against each mutant in isolation
4. **Report** — Mutations killed by failing tests are "killed"; survivors indicate test gaps

```
Original:    fun add(a: Int, b: Int) = a + b
Mutant:      fun add(a: Int, b: Int) = a - b
Test:        assertEquals(4, add(2, 2))  → FAILS → Mutant KILLED ✓
```

## Mutation Operators

### Core Operators (MVP)

| Operator | Description | Example |
|----------|-------------|---------|
| `ARITHMETIC` | Binary arithmetic | `+` → `-`, `*` → `/` |
| `CONDITIONALS_BOUNDARY` | Boundary conditions | `>` → `>=`, `<` → `<=` |
| `NEGATE_CONDITIONALS` | Negate conditionals | `==` → `!=`, `>` → `<=` |
| `RETURN_VALS` | Return values | `true` → `false`, `1` → `0` |
| `NULL_RETURNS` | Null returns | `return value` → `return null` |
| `EMPTY_RETURNS` | Empty collections | `return list` → `return emptyList()` |
| `INVERT_NEGS` | Negation | `!x` → `x` |

### Kotlin-Specific Operators

| Operator | Description |
|----------|-------------|
| `DATA_CLASS_COPY` | Mutate `copy()` parameters to defaults |
| `SEALED_WHEN` | Remove/seal `when` branches on sealed classes |
| `NULL_SAFETY` | Mutate `?.let`, `?:`, `!!` operators |
| `COROUTINE` | Mutate coroutine builders and suspend functions |

### Additional Operators

| Operator | Description |
|----------|-------------|
| `VOID_METHOD_CALLS` | Remove void method calls |
| `INCREMENTS` | `++` → `--` and vice versa |
| `TRUE_RETURNS` / `FALSE_RETURNS` | Boolean return mutations |
| `CONSTRUCTOR_CALLS` | Remove object creation |
| `NON_VOID_METHOD_CALLS` | Remove non-void method calls |

## Configuration

### Gradle Plugin DSL

```kotlin
mutationTest {
    // Auto-detected by default. Override if needed:
    // targetClasses.setFrom("build/classes/kotlin/main")
    // testClasses.setFrom("build/classes/kotlin/test")
    
    // Report formats
    reportFormats.set(setOf("html", "console"))
    
    // Speed optimizations
    enableSubsumption.set(true)      // Skip subsumed mutations
    enableWeakMutation.set(true)     // Check weak mutants first
    enableInlinedFinally.set(true)   // Detect inlined finally blocks
    enableTestOrdering.set(true)     // Run most-likely-to-kill tests first
    enableCache.set(true)            // Cache results for faster re-runs
    
    // Thresholds
    failOnScoreThreshold.set(70)     // Fail if score < 70%
    
    // Class filtering (regex patterns)
    targetClassPatterns.set(listOf("com\\.example\\..*"))
    excludeClassPatterns.set(listOf("com\\.example\\.generated\\..*"))
}
```

### JUnit Annotation

```kotlin
@MutKtTest(
    timeoutMs = 30_000,           // Per-mutant timeout
    operators = ["ARITHMETIC"],   // Specific operators only
    maxMutations = 100,           // Limit mutations per class
)
class MyTest { ... }
```

### Suppression

```kotlin
// Suppress all mutations on a class
@SuppressMutations(reason = "Generated code")
class BuildConfig { }

// Suppress specific operators
@SuppressMutations(operators = ["ARITHMETIC", "RETURN_VALS"])
class Utils { }
```

## Reports

### HTML Report
Generated at `build/reports/mutation/mutation-report.html`. Includes:
- Summary statistics (killed/survived/errors)
- Per-mutation details with operator, class, method, line number
- Color-coded status

### Console Report
Always printed to stdout with mutation score and per-class breakdown.

### Graph Report
Interactive D3.js visualization of test-mutant relationships (when enabled).

## Advanced Features

### Subsumption Analysis
Identifies mutations that are "subsumed" by others — if mutant A is killed by every test that kills mutant B, then B is subsumed by A. Skipping subsumed mutants speeds up analysis.

### Weak Mutation Testing
Checks if a mutation reaches an execution point before running the full test suite. Weak mutants that never reach execution are skipped.

### Inlined Finally Detection
Detects `finally` blocks that the compiler inlined into each exit path, preventing duplicate mutations.

### Incremental Analysis
Tracks mutation results across commits. Only re-tests mutations in changed files.

### Test Result Caching
File-based caching of mutation results. On re-run, skips previously tested mutations for faster feedback.

```kotlin
mutationTest {
    enableCache.set(true)  // Enable caching
}
```

### Test Strength Ordering
Tracks which tests kill the most mutations and runs them first. Enables faster kill detection.

```kotlin
mutationTest {
    enableTestOrdering.set(true)  // Run strongest tests first (default: true)
}
```

### Custom Mutators
MutKt supports custom mutation operators via the `MutationOperator` enum.

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Gradle Plugin (mutation-gradle-plugin)          │
│  ┌─────────────────────────────────────────────┐ │
│  │  MutationTask → auto-detect → MutationEngine│ │
│  └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│  Core Engine (mutation-core)                     │
│  ┌───────────┐ ┌──────────┐ ┌─────────────────┐ │
│  │ Mutator   │ │ Scanner  │ │ MutantClassLoad │ │
│  │ (ASM)     │ │ (ASM)    │ │ (Isolation)     │ │
│  └───────────┘ └──────────┘ └─────────────────┘ │
├─────────────────────────────────────────────────┤
│  Test Runner (mutation-test-runner)              │
│  ┌─────────────────────────────────────────────┐ │
│  │  MutKtExtension + @MutKtTest                │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## Documentation

Generate API docs:
```bash
./gradlew dokkaGenerate  # single module
./gradlew dokkaHtmlMultiModule  # all modules aggregated
```

Output: `build/dokka/htmlMultiModule/index.html`

## Requirements

- JDK 17+
- Kotlin 2.0+
- Gradle 8.0+

## License

[Apache License 2.0](LICENSE)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.
