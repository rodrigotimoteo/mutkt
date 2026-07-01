# MutKt

**Kotlin Mutation Testing — PITest-style for Kotlin/JVM**

[![CI](https://github.com/rodrigotimoteo/mutkt/actions/workflows/ci.yml/badge.svg)](https://github.com/rodrigotimoteo/mutkt/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.rodrigotimoteo/mutation-core)](https://central.sonatype.com/search?q=g:io.github.rodrigotimoteo)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

MutKt is a mutation testing library for Kotlin and Java that runs your existing test suite against mutated versions of your code. It finds gaps in your tests by verifying they actually catch bugs — not just pass.

## Android Support

MutKt fully supports Android unit tests via Robolectric. See [docs/ANDROID.md](docs/ANDROID.md) for the quick start.

**TL;DR**: Apply the plugin, add Robolectric to your test classpath, run `./gradlew mutationTest`.

## Quick Start

### Gradle Plugin (Recommended)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.10"
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.3.1"
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
    testImplementation("io.github.rodrigotimoteo:mutation-test-runner:0.3.1")
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

## What's New in 0.3.2

- **Android variant resolution fix (TvJapan scenario)** — `AgpVariantResolver` wraps the AGP `<variant>RuntimeClasspath` / `<variant>UnitTestRuntimeClasspath` in an `artifactType=jar` `ArtifactView`. Resolves the "Cannot choose between the following variants" ambiguity when the consumer app has no own product flavors but depends on a library that publishes multiple runtime sub-variants on a `flavorDimensions` axis. Add `defaultConfig.missingDimensionStrategy("brand", "production")` (or your own `flavorDimensions`) to keep variant matching deterministic.
- **Actionable error formatter** — When variant resolution still fails, the error now names the exact `ProductFlavor:<dim>=<value>` attribute the consumer is missing and suggests a one-line `missingDimensionStrategy` fix.
- **Sample modules** — `:multi-app` (consumer app with `missingDimensionStrategy`) and `:multi-shared` (library with `production` / `staging` flavors) form a TvJapan-style end-to-end repro. `./gradlew :multi-app:mutationTest` runs the full pipeline.
- **Publishing structurally impossible for samples** — `multi-app`, `multi-shared`, `mutation-sample*`, and `mutation-self-test` are excluded from publishing by an `afterEvaluate` block that disables every `publish*` / `Upload*` task at configuration time. A future "publish all" CI workflow cannot accidentally ship a sample artifact.

## What's New in 0.3.1

- **Custom JUnit engines** — `engineIds` parameter on `MutationEngine` / `ReflectionTestRunner` to restrict discovery (default includes `junit-jupiter`, `junit-vintage`, `junit-platform-suite-engine`).
- **Tag filtering** — `includeTags` / `excludeTags` honor `@Tag` / `@Tags` / `@EnabledIf` / `@EnabledOnOs` style filters.
- **MutKtExtension interceptors** — `@TestFactory` (dynamic tests) and `@ParameterizedTest` (test templates) now route through `interceptWithTracking` for per-method mutation attribution.
- **3-tier classloader hierarchy** — `TestClassLoader` isolates inline-mock agents (MockK / Mockito) from project classpath.
- **Parallel class scanning** — `generateAllMutations` runs across `ForkJoinPool` (was sequential per-class scan).
- **Coverage O(N²) → O(N)** — JaCoCo `.exec` parsed once and reused.
- **Kover 0.9.0** — verify rule enforces 85% line coverage per module.
- **AAR `libs/*.jar` extraction** — every bundled jar in an AAR is now visible to the URL classloader.
- **KMP source set support** — all common KMP source roots (ios, linux, macos, mingw, js, wasm*, common) are recognized for incremental analysis.
- **Maven Central via `gradle-nexus-publish-plugin`** — POM metadata (licenses, developers, scm) included.

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
    // targetClasses.from("build/classes/kotlin/main")
    // testClasses.from("build/classes/kotlin/test")
    
    // Report formats
    reportFormats.set(setOf("html", "console"))
    
    // Speed optimizations
    enableSubsumption.set(true)      // Skip subsumed mutations
    enableWeakMutation.set(true)     // Check weak mutants first
    enableInlinedFinally.set(true)   // Detect inlined finally blocks
    enableTestOrdering.set(true)     // Run most-likely-to-kill tests first
    enableCache.set(true)            // Cache results for faster re-runs
    
    // Thresholds
    failOnMutationScoreThreshold.set(70)  // Fail if score < 70%
    
    // Class filtering (regex patterns)
    targetClassPatterns.set(setOf("com\\.example\\..*"))
    excludeClassPatterns.set(setOf("com\\.example\\.generated\\..*"))
}
```

### JUnit Annotation

```kotlin
@MutKtTest(
    timeoutMs = 30_000,           // Per-mutant timeout
)
class MyTest { ... }
```

> `operators` and `maxMutations` are configured via the Gradle DSL, not the annotation:
> ```kotlin
> mutationTest {
>     enabledOperators.set(setOf("ARITHMETIC"))
>     maxMutationsPerClass.set(100)
> }
> ```

### Suppression

```kotlin
// Suppress all mutations on a class
@SuppressMutations(reason = "Generated code")
class BuildConfig { }

// Suppress specific operators on a class
@SuppressMutations(operators = ["ARITHMETIC", "RETURN_VALS"])
class Utils { }

// Suppress mutations on a specific method only
class Service {
    @SuppressMutations(reason = "Trivial getter")
    fun getName(): String = name

    @SuppressMutations(operators = ["ARITHMETIC"])
    fun calculate(): Int = a + b  // Only ARITHMETIC suppressed, other operators still apply
}
```

### Full Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `targetClasses` | `ConfigurableFileCollection` | auto-detected | Compiled class directories |
| `testClasses` | `ConfigurableFileCollection` | auto-detected | Test class directories |
| `classpath` | `ConfigurableFileCollection` | auto-detected | Test runtime classpath |
| `coverageExecFile` | `RegularFileProperty` | auto-detected | JaCoCo execution data file |
| `enabledOperators` | `SetProperty<String>` | MVP_OPERATORS | Mutation operator names to enable |
| `timeoutMs` | `Property<Long>` | `30000` | Per-mutant timeout in ms |
| `maxParallelMutants` | `Property<Int>` | `4` | Number of parallel mutant executions |
| `outputDir` | `DirectoryProperty` | `build/reports/mutation` | Output directory for reports |
| `failOnSurvived` | `Property<Boolean>` | `false` | Fail build if any mutants survive |
| `excludedClasses` | `SetProperty<String>` | 15 patterns | Glob patterns for classes to skip |
| `excludedMethods` | `SetProperty<String>` | 6 method names | Method names to skip |
| `maxMutationsPerClass` | `Property<Int>` | `0` | Limit mutations per class (`0` = no limit) |
| `enableIncrementalAnalysis` | `Property<Boolean>` | `true` | Only test changed classes |
| `reportFormats` | `SetProperty<String>` | `["html"]` | Report types: `html`, `console`, `csv`, `xml`, `json`, `graph` |
| `showClassScores` | `Property<Boolean>` | `true` | Per-class breakdown in reports |
| `enableCache` | `Property<Boolean>` | `false` | Cache results for faster re-runs |
| `targetClassPatterns` | `SetProperty<String>` | empty | Regex patterns to include classes |
| `targetTestPatterns` | `SetProperty<String>` | empty | Regex patterns to include test classes |
| `excludeClassPatterns` | `SetProperty<String>` | empty | Regex patterns to exclude classes |
| `excludeTestPatterns` | `SetProperty<String>` | empty | Regex patterns to exclude test classes |
| `targetPackages` | `SetProperty<String>` | empty | Packages to include (e.g. `com.example.service`) |
| `excludePackages` | `SetProperty<String>` | empty | Packages to exclude |
| `enableSubsumption` | `Property<Boolean>` | `true` | Skip subsumed mutations |
| `enableWeakMutation` | `Property<Boolean>` | `true` | Skip unreachable mutations |
| `enableInlinedFinally` | `Property<Boolean>` | `true` | Detect inlined finally blocks |
| `enableTestOrdering` | `Property<Boolean>` | `true` | Run strongest tests first |
| `ciMode` | `Property<Boolean>` | `false` | Console + XML reports for CI |
| `failOnMutationScoreThreshold` | `Property<Int>` | `0` | Fail build if mutation score below threshold |
| `isAndroid` | `Property<Boolean>` | auto-detected | True if AGP is on the classpath |
| `androidPluginType` | `Property<String>` | auto-detected | `"application"` or `"library"` |
| `androidVariant` | `Property<String>` | `"debug"` | Which Android build variant to test |
| `excludeGeneratedClasses` | `SetProperty<String>` | 20 patterns | Generated classes (R, BuildConfig, Hilt) to skip |
| `androidContext` | `Property<AndroidMutationContext>` | auto-detected | Resolved AGP variant context (internal) |

#### Deprecated properties

| Property | Replacement | Reason |
|----------|-------------|--------|
| `failOnScoreThreshold` | `failOnMutationScoreThreshold` | Name was misleading — always compared the mutation score |
| `mutantTimeoutMs` | `timeoutMs` | Duplicated the same setting with confusing precedence |
| `generateGraph` | `reportFormats.add("graph")` | Graph is one of the available report formats |
| `autoRunJaCoCo` | — | Not yet implemented; reserved for future JaCoCo agent auto-run |
| `verbose` | — | Not yet implemented; reserved for future verbose logging mode |

## Troubleshooting

### No mutations found

1. **Wrong class directories** — Set `targetClasses.setFrom("build/classes/kotlin/main")` explicitly
2. **All @SuppressMutations** — Check for class-level suppression annotations
3. **Kotlin not detected** — Verify source classes use `@kotlin.Metadata` (default for Kotlin compiler)
4. **Excluded methods** — Check `excludedMethods` property

### Tests not discovered

1. **JUnit dependency missing** — JUnit Jupiter 5.x or JUnit 4 must be on the test runtime classpath
2. **Non-public test classes** — Test classes must be public
3. **Test patterns mismatch** — Test classes must match the naming convention `*Test`, `*Tests`, `Test*`, or `*Spec` (e.g. `MyServiceTest`, `TestRunner`, `UserSpec`). Check `targetTestPatterns` and `excludeTestPatterns`.
4. **Meta-annotations** — JUnit Jupiter's standard discovery applies. `@Test`, `@ParameterizedTest`, `@RepeatedTest`, and any custom annotation meta-annotated with one of them are discovered via the JUnit Platform Launcher.
5. **Inline-mock / instrumentation agents missing** — When using MockK inline, Mockito inline, or ByteBuddy, the test runtime classpath passed to the mutation test task MUST contain the inline agent jars, and any required `-javaagent` JVM args (e.g. `-javaagent:path/to/mockk-inline.jar`) MUST be set on the JVM. The Gradle plugin inherits these from the regular `test` task; if you override `classpath` or `jvmArgs` on the `mutationTest` task, copy them through.

### Plugin not found

```kotlin
// If using plugins { } block fails, use buildscript approach:
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("io.github.rodrigotimoteo:mutation-gradle-plugin:0.3.1")
    }
}
apply(plugin = "io.github.rodrigotimoteo.mutation-kotlin")
```

### Timeout on large projects

1. **Enable subsumption** — `enableSubsumption.set(true)` (default)
2. **Enable weak mutation** — `enableWeakMutation.set(true)` (default)
3. **Enable caching** — `enableCache.set(true)` for re-runs
4. **Enable incremental** — `enableIncrementalAnalysis.set(true)` for PR branches
5. **Limit scope** — Use `targetPackages`, `targetClassPatterns`, or `maxMutationsPerClass`
6. **Increase timeout** — `timeoutMs.set(60000)` for integration tests

### HTML Report
Generated at `build/reports/mutation/mutation-report.html`. Includes:
- Summary statistics with mutation score badge (shields.io)
- Per-mutation details with source code snippets at mutation point
- Operator, class, method, line number
- Color-coded status (KILLED/SURVIVED/ERROR/TIMEOUT/NO_COVERAGE)
- Per-class breakdown with mini kill-rate bars

### Console Report
Always printed to stdout with mutation score and per-class breakdown. Includes:
- ASCII kill-rate bar
- Survived mutations list with location
- Error mutation details
- Real-time progress bar with mutations/sec throughput

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
MutKt ships 16 built-in mutation operators grouped into MVP, Kotlin-specific, and Quick Win categories. Custom operator support is not currently implemented — operators are defined by the `MutationOperator` enum in the core module.

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
./gradlew dokkaGenerate  # all modules aggregated (Dokka 2.2.0 V2)
```

Output: `build/dokka/html/index.html`

## Requirements

- JDK 17+
- Kotlin 2.1.0+
- Gradle 8.0+

## License

[Apache License 2.0](LICENSE)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.
