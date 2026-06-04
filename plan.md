# Kotlin Mutation Testing Library - MVP Plan

## Vision
Free, open-source, plug-and-play mutation testing for Kotlin (JVM + Android/KMP) that works with existing JUnit tests - no test modifications required. Alternative to paid ArcMutate/PITest Kotlin support.

## Architecture Decisions (Resolved)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Mutation Engine** | ASM + Kotlin `@Metadata` annotations | Mature, fast, PITest-proven. Parse Kotlin metadata for construct awareness |
| **Test Execution** | Custom classloader per mutant | PITest approach. Isolate mutants, coverage-guided test selection |
| **Delivery** | Gradle plugin | Native Android/JVM/KMP integration, incremental builds |
| **MVP Operators** | Core: conditionals, arithmetic, returns, nulls | 80% value. Kotlin-specific operators post-MVP |

---

## MVP Scope (v0.1.0)

### Core Features
- [ ] Gradle plugin: `id("io.github.yourorg.kotlin-mutation")`
- [ ] Bytecode mutator using ASM 9+
- [ ] Custom classloader for mutant isolation
- [ ] Coverage-guided test selection (JaCoCo integration)
- [ ] HTML/XML reports (mutation score, killed/survived, line coverage)
- [ ] Configurable: target packages, excluded classes, mutation threshold, threads

### Mutation Operators (MVP)
| Operator | Description | Example |
|----------|-------------|---------|
| `CONDITIONALS_BOUNDARY` | `>=` → `>`, `<=` → `<` | `if (x >= 0)` → `if (x > 0)` |
| `NEGATE_CONDITIONALS` | `==` → `!=`, `>` → `<=` | `if (a == b)` → `if (a != b)` |
| `ARITHMETIC` | `+` → `-`, `*` → `/`, etc. | `a + b` → `a - b` |
| `RETURN_VALS` | Return constants | `return x` → `return 0` / `return null` |
| `NULL_RETURNS` | Return null for reference types | `return list` → `return null` |
| `INVERT_NEGS` | `!x` → `x` | `if (!flag)` → `if (flag)` |
| `EMPTY_RETURNS` | Empty collections/arrays | `return list` → `return emptyList()` |

### Kotlin Metadata Awareness (MVP)
- Detect Kotlin classes via `@Metadata` annotation
- Skip synthetic methods (`$serializer`, `componentN`, `copy$default`)
- Preserve inline class value semantics
- Basic data class `copy` mutation awareness (post-MVP: full support)

---

## Post-MVP Roadmap

### v0.2.0 - Kotlin-Specific Operators
- [ ] `DATA_CLASS_COPY` - Mutate `copy()` calls
- [ ] `SEALED_WHEN_EXHAUSTIVE` - Remove `when` branches
- [ ] `COROUTINE_BUILDER` - `launch` → `async`, `runBlocking` removal
- [ ] `FLOW_OPERATOR` - `map` → `filter`, `collect` removal
- [ ] `EXTENSION_FUNCTION` - Mutate extension receivers
- [ ] `INLINE_CLASS_UNWRAP` - Mutate value class unboxing

### v0.3.0 - Performance & Scale
- [ ] Incremental mutation (only changed files)
- [ ] Parallel mutant execution (thread pool)
- [ ] Mutation caching (skip unchanged mutants)
- [ ] Test timing-based scheduling (fast tests first)

### v0.4.0 - Ecosystem
- [ ] Maven plugin
- [ ] IntelliJ/Android Studio plugin (inline results)
- [ ] CI integrations (GitHub Actions, GitLab, Bitrise)
- [ ] Baseline/comparison mode (PR diff only)

### v0.5.0 - Advanced
- [ ] Equivalent mutant detection (heuristics)
- [ ] Mutation testing for multiplatform (JS/Wasm/Native)
- [ ] Custom mutation operator API

---

## Technical Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Gradle Plugin                            │
│  - Task: mutationTest                                       │
│  - Config: targetPackages, excludes, threshold, threads    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  Mutation Engine (Core)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Classpath   │  │ Mutator     │  │ Classloader         │  │
│  │ Scanner     │──│ (ASM)       │──│ (MutantClassLoader) │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│         │                │                    │              │
│         ▼                ▼                    ▼              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           Coverage Analyzer (JaCoCo)                │    │
│  │  - Line coverage per test class                     │    │
│  │  - Test-to-mutation mapping                         │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Test Runner                              │
│  - JUnit Platform launcher                                  │
│  - Per-mutant test execution                                │
│  - Early exit on kill                                       │
│  - Timeout handling                                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Reporter                                 │
│  - HTML report (PITest-compatible format)                   │
│  - XML/JSON for CI                                          │
│  - Console summary                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## Module Structure

```
kotlin-mutation/
├── mutation-core/           # Bytecode mutation engine (ASM)
│   ├── src/main/kotlin/
│   │   ├── mutator/
│   │   │   ├── MutationOperator.kt
│   │   │   ├── ConditionalMutator.kt
│   │   │   ├── ArithmeticMutator.kt
│   │   │   ├── ReturnValueMutator.kt
│   │   │   └── NullReturnMutator.kt
│   │   ├── metadata/
│   │   │   ├── KotlinMetadataParser.kt
│   │   │   └── SyntheticMethodFilter.kt
│   │   ├── classloader/
│   │   │   └── MutantClassLoader.kt
│   │   ├── coverage/
│   │   │   └── CoverageAnalyzer.kt
│   │   └── model/
│   │       ├── Mutation.kt
│   │       ├── MutationResult.kt
│   │       └── MutationReport.kt
│   └── build.gradle.kts
├── mutation-gradle-plugin/  # Gradle plugin
│   ├── src/main/kotlin/
│   │   ├── MutationPlugin.kt
│   │   ├── MutationTask.kt
│   │   ├── MutationExtension.kt
│   │   └── reporter/
│   │       ├── HtmlReporter.kt
│   │       └── XmlReporter.kt
│   └── build.gradle.kts
├── mutation-test-runner/    # Test execution orchestration
│   ├── src/main/kotlin/
│   │   ├── TestRunner.kt
│   │   ├── MutantScheduler.kt
│   │   └── EarlyExitStrategy.kt
│   └── build.gradle.kts
├── mutation-sample/         # Demo project for manual testing
└── build.gradle.kts         # Root build
```

---

## Key Implementation Details

### ASM Mutator Pattern
```kotlin
// Each operator extends MethodVisitor
class ConditionalMutator(api: Int, mv: MethodVisitor) : MethodVisitor(api, mv) {
    override fun visitJumpInsn(opcode: Int, label: Label) {
        // Map IFGE → IFGT, IFLE → IFLT, etc.
        val mutatedOpcode = when (opcode) {
            Opcodes.IFGE -> Opcodes.IFGT
            Opcodes.IFLE -> Opcodes.IFLT
            // ...
            else -> opcode
        }
        super.visitJumpInsn(mutatedOpcode, label)
    }
}
```

### Kotlin Metadata Parsing
```kotlin
// Read @Metadata annotation from class
@Metadata(
    mv = {2, 0, 0},
    k = 1,  // 1=class, 2=interface, 3=object, 4=annotation, 5=type parameter
    d1 = {"\u0000\u0010\n\u0002\u0018\u0002..."},  // Protobuf-encoded metadata
    d2 = {"Lcom/example/MyDataClass;", "..."}
)
class MyDataClass
```

### Mutant ClassLoader
```kotlin
class MutantClassLoader(
    parent: ClassLoader,
    private val mutations: Map<String, List<Mutation>>,
    private val activeMutantId: String
) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*> {
        val mutatedBytes = applyMutations(name, activeMutantId)
        return defineClass(name, mutatedBytes, 0, mutatedBytes.size)
    }
}
```

---

## Configuration (Gradle DSL)

```kotlin
// build.gradle.kts
plugins {
    id("io.github.yourorg.kotlin-mutation") version "0.1.0"
}

mutationTesting {
    targetPackages = listOf("com.myapp.*")
    excludedClasses = listOf("**/generated/**", "**/dto/**")
    mutationThreshold = 70  // Fail build if below
    threads = 4
    timeout = 10_000  // ms per test
    operators = listOf(
        "CONDITIONALS_BOUNDARY",
        "NEGATE_CONDITIONALS",
        "ARITHMETIC",
        "RETURN_VALS",
        "NULL_RETURNS"
    )
    reports {
        html.enabled = true
        xml.enabled = true
    }
}
```

---

## Success Criteria (MVP)

- [ ] Plugin applies to Android/JVM/KMP projects
- [ ] Runs existing JUnit 5 tests without modification
- [ ] Generates mutations for target packages
- [ ] Reports mutation score ≥ 0% (functional)
- [ ] HTML report shows killed/survived per line
- [ ] Build fails when threshold not met
- [ ] Completes in < 5x normal test time for medium project

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| ASM bytecode complexity | Start with simple operators, extensive integration tests |
| Kotlin metadata parsing | Use `kotlin-reflect` or `kotlinx-metadata` library |
| Classloader isolation leaks | Weak references, explicit cleanup, integration tests |
| Android/KMP compatibility | Test matrix: JVM, Android, KMP (JS/Wasm later) |
| Performance at scale | Coverage-guided selection, early exit, parallel execution |
| Equivalent mutants | Heuristics post-MVP, user exclusion config |

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `org.ow2.asm:asm:9.7+` | Bytecode manipulation |
| `org.ow2.asm:asm-util:9.7+` | ASM utilities |
| `org.jetbrains.kotlin:kotlin-reflect` | Kotlin metadata reading |
| `org.jacoco:org.jacoco.agent` | Coverage collection |
| `org.junit.platform:junit-platform-launcher` | Test execution |
| `com.google.protobuf:protobuf-java` | Metadata protobuf parsing |

---

## Next Steps

1. **Initialize repo** - Gradle multi-module, publish to local Maven
2. **mutation-core** - ASM mutators + metadata parser + classloader
3. **mutation-test-runner** - JUnit launcher + mutant scheduling
4. **mutation-gradle-plugin** - Task + DSL + reporters
5. **mutation-sample** - Demo Android + JVM project
6. **CI/CD** - GitHub Actions, publish to Maven Central
7. **Documentation** - README, Gradle plugin portal, examples

---

## MVP Status: COMPLETE ✓

### Delivered
- **mutation-core** (4 source files + tests): ASM mutator, Kotlin metadata parser, MutantClassLoader, CoverageAnalyzer, MutationEngine
- **mutation-test-runner** (3 source files + tests): JUnit Platform launcher, reflection-based test execution, MutationTestRunner
- **mutation-gradle-plugin** (5 source files): MutationPlugin, MutationTask, MutationPluginExtension, ConsoleReportGenerator, HtmlReportGenerator
- **mutation-sample**: Calculator demo with comprehensive JUnit 5 tests

### Test Results
- 6 tests pass (4 in mutation-test-runner, 1 in mutation-core, 1 in mutation-sample)
- Integration test on sample: **16 mutations generated, 15 killed, 1 survived**
- Survived mutation: `CONDITIONALS_BOUNDARY max:47` (`if (a > b)` → `if (a >= b)`) - expected behavior, max(5,5) returns 5 either way

### MVP Mutation Operators (7)
1. `CONDITIONALS_BOUNDARY`: `<` ↔ `<=`, `>` ↔ `>=`
2. `NEGATE_CONDITIONALS`: `==` ↔ `!=`, `<` ↔ `>=`, etc.
3. `INVERT_NEGS`: `IFEQ` ↔ `IFNE`
4. `ARITHMETIC`: `+` ↔ `-`, `*` ↔ `/`
5. `RETURN_VALUES`: primitive returns → 0
6. `NULL_RETURNS`: reference returns → null
7. `EMPTY_RETURNS`: collections/arrays → empty

### Bugfixes During MVP
- `ClassVisitor.visitMethod()` returns null when no delegate → use direct visitor pattern
- Kotlin `executor.submit { ... }` infers `Runnable` (returns null) → wrap in `Callable<MutationResult>`
- `ClassReader.SKIP_DEBUG` strips line numbers → remove to preserve source mapping
- MutantClassLoader needs test class bytes in addition to main class bytes for proper test loading

---

## Open Questions

- [ ] Package name / Maven coordinates (`io.github.yourorg`?) → **DECIDED: `com.github.rodrigotimoteo`**
- [ ] License (Apache 2.0 / MIT?) → **DECIDED: TBD - to be added**
- [ ] Minimum Kotlin version (1.9? 2.0?) → **DECIDED: 2.1.10+**
- [ ] Minimum Gradle version (8.0+?) → **DECIDED: 8.10+**
- [ ] JUnit 4 support or JUnit 5 only? → **DECIDED: JUnit 5 only (MVP)**

### Next Steps (Post-MVP)
- [ ] Add LICENSE file
- [ ] Publish to Maven Central
- [ ] Kotlin-specific mutations: data class copy, sealed when, coroutines
- [ ] HTML report improvements (mutation source diff)
- [ ] Android support verification
- [ ] CI integration guide