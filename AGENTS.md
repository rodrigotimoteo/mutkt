# MutKt

Kotlin mutation testing library — PITest-style for Kotlin/JVM. Users add Gradle plugin, run existing tests against mutated bytecode. Zero test changes required.

## Modules

| Module | Purpose | Key class |
|--------|---------|-----------|
| `mutation-core` | ASM-based bytecode scanner, mutators, engine, reports | `MutationEngine`, `Mutator` |
| `mutation-test-runner` | JUnit Platform launcher, reflection-based test execution | `MutationTestRunner`, `MutKtExtension` |
| `mutation-gradle-plugin` | Gradle plugin, DSL, task wiring, HTML/Console reports | `MutationPlugin`, `MutationTask` |
| `mutation-sample` | Demo app — **not published**, excluded from kover/publishing | `Calculator` |

## Build Commands

```bash
# Full build (compile + lint + test)
./gradlew build

# Tests only (all modules)
./gradlew test

# Single module tests
./gradlew :mutation-core:test
./gradlew :mutation-test-runner:test
./gradlew :mutation-gradle-plugin:test

# Lint (CI runs ktlint then detekt)
./gradlew ktlintCheck
./gradlew detekt
./gradlew ktlintFormat  # auto-fix

# Coverage reports
./gradlew koverHtmlReport
# Per-module report: mutation-*/build/reports/kover/html/index.html

# Mutation test (the library testing itself)
./gradlew mutationTest

# Documentation
./gradlew dokkaHtml
```

## CI Order

CI runs: `ktlintCheck` → `detekt` → `test` → `build` → `koverXmlReport` (Codecov).

## Testing Quirks

- **Gradle plugin tests need `--add-opens`** — already configured in `mutation-gradle-plugin/build.gradle.kts` (`--add-opens=java.base/java.lang=ALL-UNNAMED` etc.)
- **Integration test depends on mutation-sample** — `MutationEngineIntegrationTest` requires `mutation-sample` to be built first. Run `./gradlew :mutation-sample:build` before if it fails.
- **MockK** available in all test modules via `libs.mockk`
- **ASM bytecode construction** — scanner/applier tests build minimal classes using `ClassWriter(Opcodes.V21)` with `<init>` constructor. Use the `buildClassWithMethod` helper pattern from existing tests.
- **MutationRegistry is a global singleton** — tests must call `MutationRegistry.reset()` in `@BeforeEach`/`@AfterEach` to avoid bleeding state.
- **Inline comments in function argument lists** cause ktlint failures — move comments to separate lines.

## Architecture

### Bytecode Mutation Pipeline
```
MutantClassLoader → loads original + mutated bytecode
Mutator.scanMutations() → finds mutation points via ClassVisitor
Mutator.applyMutation() → applies single mutation to bytecode
MutationEngine.runMutationTesting() → orchestrates scan → mutate → test → report
```

### Key Internal Types
- `Mutator` — main entry for scanning/applying mutations. Contains private visitor classes (`MutationScannerVisitor`, `MutationApplierMethodVisitor`)
- `MutationInfo` — data class for a single mutation point (operator, class, method, line, opcode)
- `MutationRegistry` — global singleton tracking mutations in JUnit runs
- `MutantClassLoader` — classloader that replaces target class bytes with mutated versions

### Kotlin-Specific Mutators (internal objects)
- `CoroutineMutator`, `DataClassCopyMutator`, `NullSafetyMutator`, `SealedClassWhenMutator`
- Detection uses `@kotlin.Metadata` annotation presence on bytecode
- `isKotlinSynthetic` helper (precise pattern) vs `isKotlinSyntheticMethod` (broad — avoid)

## Conventions

- **Group ID**: `io.github.rodrigotimoteo` (verified Maven Central namespace — NOT `com.github.rodrigotimoteo`)
- **Kotlin 2.1.10**, JVM target 21, ASM 9.10.1
- **Version catalog**: `gradle/libs.versions.toml` — all deps via `libs.*`, no inline versions
- **Publishing**: Maven Central via Sonatype (needs `SONATYPE_USERNAME`/`SONATYPE_PASSWORD` env vars + GPG key)
- **Coverage threshold**: 85% line per module enforced by kover
- **Detekt**: `maxIssues: 0`, `LongMethod: 60`, `LongParameterList: 8/10`, `ComplexMethod: 15`
- **18 mutation operators**: 7 MVP + 6 Kotlin-specific + 5 additional

## Known Gotchas

- `isCollectionOrArrayStatic` uses **dotted** class names (`java.util.List`), not slashed (`java/util/List`) — `Type.className` returns dotted form
- `MutantClassLoader` caches `Class<*>` instances in `mutatedCache` — calling `defineClass` twice throws `LinkageError`
- `@SuppressMutations` annotation presence = suppress ALL mutations (not just listed operators)
- OWASP dep-check uses NVD data feed (no API key needed) — `failOnError=false`
- GitHub repo: `rodrigotimoteo/mutkt`
