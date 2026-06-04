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
- [x] Expand library - see EXPANSION PLAN below

---

# EXPANSION PLAN

## Decisions (Grilled)

| Decision | Choice |
|----------|--------|
| Plugin execution model | Separate `mutationTest` task (not auto-attached to `test`) |
| Auto-discovery scope | Full auto-detect (sourceSets, classpath, everything) |
| Kotlin operators | All 4: data class copy, sealed when, null-safety, coroutines |
| Publishing | Both Maven Central + GitHub Packages |
| README scope | Minimal (quick start + badge + DSL reference + examples) |
| Kotlin operator approach | Phased: bytecode + metadata first, K2 compiler plugin later |
| Test strategy | Unit + integration (hand-crafted bytecode + sample project) |
| Implementation order | Auto-discovery → Kotlin operators → Publish |

## Phase 1: Auto-Discovery (Close the Gap)

**Goal:** Zero-config. User adds plugin, runs `gradlew mutationTest`. Done.

### Changes to `mutation-gradle-plugin`

1. **Auto-detect `sourceSets`** in `MutationPlugin.kt`:
   ```kotlin
   val mainSourceSet = project.extensions.getByType(SourceSetContainer::class.java)
       .getByName("main")
   val testSourceSet = project.extensions.getByType(SourceSetContainer::class.java)
       .getByName("test")
   
   extension.targetClasses.from(mainSourceSet.output)
   extension.testClasses.from(testSourceSet.output)
   extension.classpath.from(testSourceSet.runtimeClasspath)
   ```

2. **Auto-resolve JaCoCo** in `MutationTask.kt`:
   ```kotlin
   // Check if JaCoCo plugin is applied
   val jacocoPlugin = project.plugins.findPlugin("jacoco")
   if (jacocoPlugin != null) {
       // Find .exec file from last test run
       val execFile = project.file("${project.buildDir}/jacoco/test.exec")
       if (execFile.exists()) {
           extension.coverageExecFile.set(execFile)
       }
   }
   ```

3. **Auto-configure test classpath** in `MutationTask.kt`:
   ```kotlin
   // Include all dependencies in classpath
   extension.classpath.from(project.configurations.getByName("testRuntimeClasspath"))
   ```

4. **Update DSL** in `MutationPluginExtension.kt`:
   ```kotlin
   open class MutationPluginExtension(project: Project) {
       val targetClasses: ConfigurableFileCollection = project.objects.fileCollection()
       val testClasses: ConfigurableFileCollection = project.objects.fileCollection()
       val classpath: ConfigurableFileCollection = project.objects.fileCollection()
       val coverageExecFile: RegularFileProperty = project.objects.fileProperty()
       val timeoutMs: Property<Long> = project.objects.property(Long::class.java)
       val maxParallelMutants: Property<Int> = project.objects.property(Int::class.java)
       val excludedClasses: SetProperty<String> = project.objects.setProperty(String::class.java)
       val excludedMethods: SetProperty<String> = project.objects.setProperty(String::class.java)
       
       init {
           timeoutMs.set(30000)
           maxParallelMutants.set(Runtime.getRuntime().availableProcessors())
           excludedClasses.addAll("**/*Test", "**/*_", "**/BuildConfig", "**/R.class")
       }
   }
   ```

5. **Add default excludes** in `MutationPlugin.kt`:
   ```kotlin
   extension.excludedClasses.addAll(
       "**/*Test",           // Test classes
       "**/*_",              // Generated underscore classes
       "**/BuildConfig",     // Android BuildConfig
       "**/R.class",         // Android R class
       "**/databinding/**",  // Data binding
       "**/BR.class",        // Data binding BR
       "**/*_Factory",       // Dagger factories
       "**/*_MembersInjector", // Dagger injectors
       "**/*Module_*",       // Dagger modules
       "**/*_Impl",          // Generated implementations
       "**/META-INF/**"      // Metadata
   )
   ```

6. **Add lifecycle logging** in `MutationTask.kt`:
   ```kotlin
   @TaskAction
   fun runMutationTests() {
       logger.lifecycle("=== Kotlin Mutation Testing ===")
       logger.lifecycle("Target classes: ${extension.targetClasses.files}")
       logger.lifecycle("Test classes: ${extension.testClasses.files}")
       logger.lifecycle("Classpath size: ${extension.classpath.files.size}")
       // ... run mutation testing ...
   }
   ```

### Files to modify
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPlugin.kt`
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPluginExtension.kt`
- `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt`

### Verification
- Create `mutation-sample-auto` module with ZERO config (just plugin apply)
- Run `gradlew mutationTest` → should auto-discover and run
- Verify HTML report generated

---

## Phase 2: Kotlin-Specific Operators

**Goal:** Catch Kotlin-specific bugs that bytecode-only mutators miss.

### New Operators (4)

#### 2.1 Data Class Copy Mutations
**Strategy:** Find `copy()` method in data classes, mutate individual parameters.

```kotlin
// Original
data class User(val name: String, val age: Int)
val user = User("Alice", 30)
val copy = user.copy(age = 31)

// Mutant 1: copy(name = "") 
// Mutant 2: copy(age = 0)
// Mutant 3: copy() (all defaults)
```

**Implementation:**
1. Parse `@Metadata` annotations to find data classes
2. In bytecode, `copy()` has signature: `copy(name: String, age: Int, ...): User`
3. Create mutant by replacing each parameter with default/null/0/empty
4. Detect via: class has `@Metadata` with `isData = true`

**Files to add:**
- `mutation-core/src/main/kotlin/.../mutator/DataClassCopyMutator.kt`
- `mutation-core/src/test/kotlin/.../mutator/DataClassCopyMutatorTest.kt`

#### 2.2 Sealed Class When Mutations
**Strategy:** Find `when` expressions on sealed classes, remove branches or return wrong values.

```kotlin
// Original
sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val msg: String) : Result()
}

fun handle(result: Result) = when (result) {
    is Result.Success -> result.data
    is Result.Error -> throw Exception(result.msg)
}

// Mutant 1: Remove `is Result.Success` branch → throw IllegalStateException
// Mutant 2: Remove `is Result.Error` branch → throw IllegalStateException
// Mutant 3: Swap branch bodies
```

**Implementation:**
1. Parse `@Metadata` to find sealed classes
2. In bytecode, `when` compiles to `tableswitch` or `lookupswitch`
3. Mutant: replace case with `throw new IllegalStateException("Mutation")`
4. Or: swap case bodies

**Files to add:**
- `mutation-core/src/main/kotlin/.../mutator/SealedClassWhenMutator.kt`
- `mutation-core/src/test/kotlin/.../mutator/SealedClassWhenMutatorTest.kt`

#### 2.3 Null-Safety Operator Mutations
**Strategy:** Mutate `?.let`, `?:`, `!!` operators.

```kotlin
// Original
val result = input?.trim() ?: "default"

// Mutant 1: input?.trim() → input.trim()  (remove null check)
// Mutant 2: input?.trim() → null          (always null)
// Mutant 3: ?: "default" → ?: ""          (empty default)
// Mutant 4: input!!                        (force unwrap)
```

**Implementation:**
1. Detect `INVOKEVIRTUAL` with null check (has `CHECKCAST` before)
2. Mutant: remove null check, or replace with `null` push
3. For `!!`: replace with `INVOKEVIRTUAL` without null check
4. For `?:`: replace default value

**Files to add:**
- `mutation-core/src/main/kotlin/.../mutator/NullSafetyMutator.kt`
- `mutation-core/src/test/kotlin/.../mutator/NullSafetyMutatorTest.kt`

#### 2.4 Coroutine Mutations
**Strategy:** Mutate `runBlocking`, `launch`, `async`, `await`.

```kotlin
// Original
suspend fun fetchData(): String = withContext(Dispatchers.IO) {
    api.getData()
}

// Mutant 1: Skip coroutine body → return ""
// Mutant 2: Throw CancellationException
// Mutant 3: Remove withContext → run on wrong dispatcher
```

**Implementation:**
1. Detect `suspend` functions via `ACC_SYNCHRONIZED` flag
2. Detect coroutine builders via method name matching
3. Mutant: replace body with `return ""` or `throw CancellationException`
4. For `withContext`: remove dispatcher argument

**Files to add:**
- `mutation-core/src/main/kotlin/.../mutator/CoroutineMutator.kt`
- `mutation-core/src/test/kotlin/.../mutator/CoroutineMutatorTest.kt`

### Integration with MutationOperator enum

```kotlin
enum class MutationOperator(val operatorName: String, val description: String) {
    // Existing MVP operators...
    
    // Kotlin-specific (new)
    DATA_CLASS_COPY("DATA_CLASS_COPY", "Mutate data class copy() parameters"),
    SEALED_WHEN("SEALED_WHEN", "Remove/seal class when branches"),
    NULL_SAFETY("NULL_SAFETY", "Mutate ?.let, ?:, !! operators"),
    COROUTINE("COROUTINE", "Mutate coroutine builders and suspend functions"),
    
    companion object {
        val MVP_OPERATORS = setOf(/* existing */)
        val KOTLIN_OPERATORS = setOf(DATA_CLASS_COPY, SEALED_WHEN, NULL_SAFETY, COROUTINE)
        val ALL_OPERATORS = MVP_OPERATORS + KOTLIN_OPERATORS
    }
}
```

### Files to modify
- `mutation-core/src/main/kotlin/.../mutator/MutationOperator.kt` (add new operators)
- `mutation-core/src/main/kotlin/.../metadata/KotlinMetadataParser.kt` (detect data/sealed classes)

### Verification
- Add `data class` sample to `mutation-sample`
- Add `sealed class` sample to `mutation-sample`
- Add coroutine sample to `mutation-sample`
- Run integration test: verify new mutations generated and killed

---

## Phase 3: Publish to Maven Central + GitHub Packages

**Goal:** Others can use the library via `implementation("com.github.rodrigotimoteo:mutation-core:0.1.0")`.

### Publishing Setup

1. **Add publishing plugins** to root `build.gradle.kts`:
   ```kotlin
   plugins {
       id("maven-publish")
       id("signing")
   }
   ```

2. **Configure Maven Central** (Sonatype OSSRH):
   - Group ID: `com.github.rodrigotimoteo`
   - Artifact IDs: `mutation-core`, `mutation-test-runner`, `mutation-gradle-plugin`
   - Version: `0.1.0`

3. **Add POM metadata**:
   ```kotlin
   publishing {
       publications {
           create<MavenPublication>("maven") {
               groupId = "com.github.rodrigotimoteo"
               artifactId = "mutation-core"
               version = "0.1.0"
               
               pom {
                   name.set("Kotlin Mutation Testing - Core")
                   description.set("PITest-style mutation testing for Kotlin")
                   url.set("https://github.com/rodrigotimoteo/mutationKotlin")
                   licenses {
                       license {
                           name.set("Apache License, Version 2.0")
                           url.set("https://www.apache.org/licenses/LICENSE-2.0")
                       }
                   }
               }
           }
       }
   }
   ```

4. **Configure signing** (GPG):
   ```kotlin
   signing {
       val signingKey = System.getenv("GPG_SIGNING_KEY")
       val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
       useInMemoryPgpKeys(signingKey, signingPassword)
       sign(publishing.publications["maven"])
   }
   ```

5. **Add GitHub Packages**:
   ```kotlin
   publishing {
       repositories {
           maven {
               name = "GitHubPackages"
               url = uri("https://maven.pkg.github.com/rodrigotimoteo/mutationKotlin")
               credentials {
                   username = System.getenv("GITHUB_ACTOR")
                   password = System.getenv("GITHUB_TOKEN")
               }
           }
       }
   }
   ```

6. **Create publishing workflow** (`.github/workflows/publish.yml`):
   ```yaml
   name: Publish
   on:
     release:
       types: [created]
   jobs:
     publish:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with:
             java-version: '21'
             distribution: 'corretto'
         - run: ./gradlew publish
           env:
             GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}
             GPG_SIGNING_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
   ```

7. **Add README.md**:
   ```markdown
   # Kotlin Mutation Testing
   
   Free, plug-and-play mutation testing for Kotlin. Works with existing JUnit 5 tests.
   
   [![Maven Central](https://img.shields.io/maven-central/v/com.github.rodrigotimoteo/mutation-gradle-plugin)](https://central.sonatype.com/artifact/com.github.rodrigotimoteo/mutation-gradle-plugin)
   
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
       
       excludedClasses.addAll("**/Generated*", "**/BuildConfig")
   }
   ```
   
   ## Mutation Operators
   
   ### Standard (7)
   - Conditionals Boundary (`<` ↔ `<=`)
   - Negate Conditionals (`==` ↔ `!=`)
   - Invert Negs (`IFEQ` ↔ `IFNE`)
   - Arithmetic (`+` ↔ `-`, `*` ↔ `/`)
   - Return Values (primitives → 0)
   - Null Returns (references → null)
   - Empty Returns (collections → empty)
   
   ### Kotlin-Specific (4)
   - Data Class Copy (mutate copy() parameters)
   - Sealed Class When (remove branches)
   - Null-Safety (`?.let`, `?:`, `!!`)
   - Coroutines (skip/suspend mutations)
   ```

### Files to add
- `README.md`
- `.github/workflows/publish.yml`
- `LICENSE` (Apache 2.0)
- Root `build.gradle.kts` (publishing config)

### Verification
- Run `./gradlew publishToMavenLocal`
- Verify artifacts in `~/.m2/repository/com/github/rodrigotimoteo/`
- Test locally with `implementation("com.github.rodrigotimoteo:mutation-core:0.1.0")`

---

## Implementation Order

1. **Phase 1: Auto-Discovery** (1-2 hours)
   - Modify Gradle plugin to auto-detect sourceSets
   - Add JaCoCo auto-detection
   - Add default excludes
   - Test with zero-config sample

2. **Phase 2: Kotlin Operators** (2-3 hours)
   - Implement DataClassCopyMutator
   - Implement SealedClassWhenMutator
   - Implement NullSafetyMutator
   - Implement CoroutineMutator
   - Add samples and tests

3. **Phase 3: Publish** (30 min)
   - Configure Maven Central + GitHub Packages
   - Add README
   - Add LICENSE
   - Test with `publishToMavenLocal`

**Total estimated time:** 4-6 hours

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| JaCoCo agent not available | Low | Fall back to running all tests (no coverage filtering) |
| Kotlin metadata parsing fails | Medium | Skip Kotlin-specific operators, continue with bytecode-only |
| Sonatype OSSRH account not set up | High | Use GitHub Packages as fallback |
| GPG signing fails | Medium | Skip signing for initial release, add later |
| Coroutine mutations break test execution | High | Wrap in try-catch, mark mutant as ERROR not KILLED |

---

## Open Questions (Remaining)

- [ ] Sonatype OSSRH account credentials?
- [ ] GPG key pair available?
- [ ] GitHub repository already created?
- [ ] Version strategy: 0.1.0 or 1.0.0?
- [ ] License: Apache 2.0 or MIT?