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

- [ ] Sonatype OSSRH account credentials? → **DECIDED: TBD - needs account**
- [ ] GPG key pair available? → **DECIDED: TBD - needs key generation**
- [ ] GitHub repository already created? → **DECIDED: TBD - needs repo creation**
- [ ] Version strategy: 0.1.0 or 1.0.0? → **DECIDED: 0.1.0**
- [ ] License: Apache 2.0 or MIT? → **DECIDED: Apache 2.0**

---

# EXPANSION STATUS: COMPLETE ✓

## Delivered

### Phase 1: Auto-Discovery
- ✅ `MutationPlugin.kt`: Auto-detect sourceSets, classpath, JaCoCo
- ✅ `MutationPluginExtension.kt`: Full DSL with targetClasses, testClasses, excludedClasses, excludedMethods
- ✅ `MutationTask.kt`: Auto-detect JaCoCo .exec file, lifecycle logging

### Phase 2: Kotlin-Specific Operators
- ✅ `DataClassCopyMutator.kt`: Mutate copy() parameters
- ✅ `SealedClassWhenMutator.kt`: Remove/mutate sealed class when branches
- ✅ `NullSafetyMutator.kt`: Mutate ?., !!, ?: operators
- ✅ `CoroutineMutator.kt`: Mutate suspend functions and coroutine builders
- ✅ `MutationOperator.kt`: Added DATA_CLASS_COPY, SEALED_WHEN, NULL_SAFETY, COROUTINE

### Phase 3: Publishing & Docs
- ✅ `README.md`: Quick start, configuration, operator reference
- ✅ `LICENSE`: Apache 2.0
- ✅ `build.gradle.kts`: Version 0.1.0, group com.github.rodrigotimoteo

## Test Results

| Module | Tests | Status |
|--------|-------|--------|
| mutation-core | 2 | ✅ PASS |
| mutation-sample | 17 | ✅ PASS |
| mutation-test-runner | 6 | ✅ PASS |
| **Total** | **25** | **✅ PASS** |

## Git History

```
dc80076 docs: update plan.md with MVP completion status and decisions
af52557 feat(mvp): implement mutation testing MVP
7fdbfb8 feat: implement mutation-test-runner and mutation-gradle-plugin modules
4d2cbd6 feat: implement mutation-core with ASM mutators, metadata parser, classloader, coverage analyzer, and engine
```

## What's Left for Production

1. **Publish to Maven Central**: Need Sonatype OSSRH account + GPG key
2. **GitHub Packages**: Need repo creation + GITHUB_TOKEN
3. **Integration test with real project**: Test plugin on external project
4. **CI/CD**: GitHub Actions workflow for automated publishing

---

# EXPANSION 2: Full Feature Parity

## Decisions (Grilled)

| Decision | Choice |
|----------|--------|
| Scope | All phases (full expansion) |
| Operators priority | All 5: VOID_METHOD_CALLS, INCREMENTS, TRUE/FALSE_RETURNS, CONSTRUCTOR_CALLS, NON_VOID_METHOD_CALLS |
| Suppression | All 3: annotation + comment + DSL config |
| Incremental analysis | Both: file-based history + coverage-based skipping |
| Timeout detection | Both: thread-based timeout + bytecode injection |
| Report formats | HTML, XML, JSON |
| Equivalent mutation detection | Hybrid: basic heuristics + optional dataflow |
| Implementation order | Operators → suppression → incremental → reports |
| Testing strategy | Unit + integration (hand-crafted bytecode + sample project) |
| Session scope | Everything (full expansion) |

## Phase 4: New Operators (Quick Wins)

### 4.1 VOID_METHOD_CALLS

**What:** Remove calls to void methods (side effects).

**Why:** Catches tests that don't verify side effects. E.g., `userService.save(user)` → removed, test still passes = weak test.

**Implementation:**
```kotlin
// In MutationScannerMethodVisitor
override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
    val returnType = Type.getReturnType(descriptor)
    if (returnType.sort == Type.VOID && !isConstructor(name)) {
        // This is a void method call - can be mutated
        mutations.add(MutationInfo(
            operator = MutationOperator.VOID_METHOD_CALLS,
            ...
        ))
    }
}
```

**Mutation:** Replace `INVOKEVIRTUAL` with `POP` (discard receiver) or `POP2` (for long/double).

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add to scanner)
- `mutation-core/src/test/kotlin/.../mutator/VoidMethodCallMutatorTest.kt`

### 4.2 INCREMENTS

**What:** Replace `++` with `--` and vice versa.

**Why:** Simple but effective. Catches tests that don't verify loop counters.

**Implementation:**
```kotlin
override fun visitIincInsn(varIndex: Int, increment: Int) {
    if (MutationOperator.INCREMENTS in enabledOperators) {
        val mutated = -increment
        mutations.add(MutationInfo(
            operator = MutationOperator.INCREMENTS,
            ...
            description = "IINC: $increment -> $mutated",
            metadata = mapOf("originalIncrement" to increment.toString(), "mutatedIncrement" to mutated.toString())
        ))
    }
}
```

**Mutation:** Replace `IINC var, 1` with `IINC var, -1`.

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add to scanner)
- `mutation-core/src/test/kotlin/.../mutator/IncrementMutatorTest.kt`

### 4.3 TRUE_RETURNS / FALSE_RETURNS

**What:** Replace boolean return values with true/false.

**Why:** Catches weak boolean assertions. E.g., `fun isActive(): Boolean = true` → `return false`, test still passes.

**Implementation:**
```kotlin
override fun visitInsn(opcode: Int) {
    when (opcode) {
        Opcodes.ICONST_0 -> {
            if (MutationOperator.FALSE_RETURNS in enabledOperators) {
                mutations.add(MutationInfo(operator = MutationOperator.FALSE_RETURNS, ...))
            }
        }
        Opcodes.ICONST_1 -> {
            if (MutationOperator.TRUE_RETURNS in enabledOperators) {
                mutations.add(MutationInfo(operator = MutationOperator.TRUE_RETURNS, ...))
            }
        }
    }
}
```

**Mutation:** Replace `ICONST_0` with `ICONST_1` and vice versa.

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add to scanner)
- `mutation-core/src/test/kotlin/.../mutator/BooleanReturnMutatorTest.kt`

### 4.4 CONSTRUCTOR_CALLS

**What:** Remove constructor calls (object creation).

**Why:** Catches tests that don't verify object creation. E.g., `val user = User("Alice")` → removed, test still passes.

**Implementation:**
```kotlin
override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
    if (name == "<init>" && MutationOperator.CONSTRUCTOR_CALLS in enabledOperators) {
        // This is a constructor call - can be mutated
        mutations.add(MutationInfo(
            operator = MutationOperator.CONSTRUCTOR_CALLS,
            ...
        ))
    }
}
```

**Mutation:** Replace `INVOKESPECIAL <init>` with `POP` (discard uninitialized object).

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add to scanner)
- `mutation-core/src/test/kotlin/.../mutator/ConstructorCallMutatorTest.kt`

### 4.5 NON_VOID_METHOD_CALLS

**What:** Remove non-void method calls (ignore return value).

**Why:** Catches tests that don't use return values. E.g., `val result = calculate()` → `calculate()`, test still passes.

**Implementation:**
```kotlin
override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
    val returnType = Type.getReturnType(descriptor)
    if (returnType.sort != Type.VOID && !isConstructor(name)) {
        // This is a non-void method call - can be mutated
        mutations.add(MutationInfo(
            operator = MutationOperator.NON_VOID_METHOD_CALLS,
            ...
        ))
    }
}
```

**Mutation:** Replace `INVOKEVIRTUAL` with `POP` (discard return value).

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add to scanner)
- `mutation-core/src/test/kotlin/.../mutator/NonVoidMethodCallMutatorTest.kt`

### Update MutationOperator enum

```kotlin
// Add to MutationOperator.kt
VOID_METHOD_CALLS("VOID_METHOD_CALLS", "Remove calls to void methods"),
INCREMENTS("INCREMENTS", "Replace ++ with -- and vice versa"),
TRUE_RETURNS("TRUE_RETURNS", "Replace boolean returns with true"),
FALSE_RETURNS("FALSE_RETURNS", "Replace boolean returns with false"),
CONSTRUCTOR_CALLS("CONSTRUCTOR_CALLS", "Remove constructor calls"),
NON_VOID_METHOD_CALLS("NON_VOID_METHOD_CALLS", "Remove non-void method calls (ignore return)"),

companion object {
    val QUICK_WIN_OPERATORS = setOf(
        VOID_METHOD_CALLS, INCREMENTS, TRUE_RETURNS, FALSE_RETURNS,
        CONSTRUCTOR_CALLS, NON_VOID_METHOD_CALLS
    )
    val ALL_OPERATORS = MVP_OPERATORS + KOTLIN_OPERATORS + QUICK_WIN_OPERATORS
}
```

---

## Phase 5: Suppression & Configuration

### 5.1 @SuppressMutations Annotation

**What:** Skip mutations on specific classes or functions.

**Implementation:**
```kotlin
// Create annotation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuppressMutations(
    val operators: Array<String> = [],  // Empty = suppress all
    val reason: String = ""
)
```

**Usage:**
```kotlin
@SuppressMutations(reason = "Generated code")
class BuildConfig { }

@SuppressMutations(operators = ["ARITHMETIC"], reason = "Trivial getter")
fun getName(): String = name
```

**Files:**
- `mutation-core/src/main/kotlin/.../annotation/SuppressMutations.kt`
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (check annotation before adding mutations)

### 5.2 Comment-Based Line Suppression

**What:** Skip mutations on specific lines via comments.

**Implementation:**
```kotlin
// In MutationScannerMethodVisitor
override fun visitLineNumber(line: Int, start: Label?) {
    currentLineNumber = line
    // Check if this line has suppression comment
    if (hasSuppressionComment(line)) {
        suppressCurrentLine = true
    }
    super.visitLineNumber(line, start)
}
```

**Comments:**
- `// mutflow:ignore` - Skip all mutations on this line
- `// mutflow:ignore:ARITHMETIC` - Skip specific operator
- `// mutflow:falsePositive` - Mark as false positive (for reporting)

**Files:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (read source files, check comments)

### 5.3 DSL Configuration

**What:** Exclude methods via build.gradle.kts.

**Already implemented** in `MutationPluginExtension.kt`:
```kotlin
mutationTest {
    excludedMethods.addAll("main", "toString", "hashCode", "equals")
    excludedClasses.addAll("**/Generated*", "**/BuildConfig")
}
```

**Enhance with:**
```kotlin
mutationTest {
    // New options
    failOnScoreThreshold.set(80)  // Fail if mutation score < 80%
    failOnCoverageThreshold.set(90)  // Fail if line coverage < 90%
    maxMutationsPerClass.set(100)  // Limit mutations per class
    enableIncrementalAnalysis.set(true)  // Use cached results
}
```

**Files:**
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPluginExtension.kt`
- `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt`

---

## Phase 6: Incremental Analysis & Reports

### 6.1 File-Based History

**What:** Save mutation results to `.mutation-history` file.

**Implementation:**
```kotlin
data class MutationHistory(
    val timestamp: Long,
    val classHash: String,  // SHA-256 of class bytecode
    val testHash: String,   // SHA-256 of test bytecode
    val results: Map<String, MutationStatus>  // mutationId -> status
)

class MutationHistoryManager(
    private val historyFile: File
) {
    fun load(): MutationHistory { ... }
    fun save(history: MutationHistory) { ... }
    fun getReusableResults(
        classBytes: ByteArray,
        testBytes: ByteArray,
        oldHistory: MutationHistory
    ): Map<String, MutationStatus> { ... }
}
```

**Logic:**
1. Load previous history
2. For each class: compute SHA-256 of bytecode
3. If class unchanged and test unchanged → reuse previous results
4. If class changed → re-run mutations for that class only
5. Save new history

**Files:**
- `mutation-core/src/main/kotlin/.../engine/MutationHistoryManager.kt`
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt` (use history)

### 6.2 Coverage-Based Skipping

**What:** Use JaCoCo coverage data to skip uncovered code.

**Implementation:**
```kotlin
class CoverageAnalyzer {
    fun getCoveredLines(execFile: File): Set<Int> { ... }
    fun isLineCovered(lineNumber: Int, coveredLines: Set<Int>): Boolean { ... }
}

// In MutationEngine
val coveredLines = coverageAnalyzer.getCoveredLines(coverageExecFile)
val filteredMutations = mutations.filter { mutation ->
    coverageAnalyzer.isLineCovered(mutation.lineNumber, coveredLines)
}
```

**Files:**
- `mutation-core/src/main/kotlin/.../coverage/CoverageAnalyzer.kt` (enhance)
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt` (use coverage)

### 6.3 Timeout Detection

**What:** Detect infinite loops from mutations.

**Thread-Based Timeout:**
```kotlin
fun runWithTimeout(timeoutMs: Long, block: () -> Unit): Boolean {
    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit(Callable { block(); true })
    return try {
        future.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        future.cancel(true)
        false  // Timed out
    }
}
```

**Bytecode Injection:**
```kotlin
// Inject timeout check at loop headers
// In ClassVisitor
override fun visit跳JumpInsn(opcode: Int, label: Label) {
    if (opcode in listOf(Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE)) {
        // This is a loop condition - inject timeout check
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "MutationRegistry", "checkTimeout", "()V", false)
    }
    super.visitJumpInsn(opcode, label)
}
```

**Files:**
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt` (thread-based timeout)
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (bytecode injection)

### 6.4 Report Formats

**XML Report:**
```kotlin
object XmlReportGenerator {
    fun generate(report: MutationReport, outputDir: File): File {
        val xml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<mutationTestReport>")
            appendLine("  <statistics>")
            appendLine("    <mutations>${report.totalMutations}</mutations>")
            appendLine("    <killed>${report.killedMutations}</killed>")
            appendLine("    <survived>${report.survivedMutations}</survived>")
            appendLine("  </statistics>")
            appendLine("  <mutations>")
            for (result in report.results) {
                appendLine("    <mutation>")
                appendLine("      <id>${result.mutation.id}</id>")
                appendLine("      <status>${result.status}</status>")
                appendLine("      <operator>${result.mutation.operator.operatorName}</operator>")
                appendLine("      <line>${result.mutation.lineNumber}</line>")
                appendLine("    </mutation>")
            }
            appendLine("  </mutations>")
            appendLine("</mutationTestReport>")
        }
        val file = File(outputDir, "mutations.xml")
        file.writeText(xml)
        return file
    }
}
```

**JSON Report:**
```kotlin
object JsonReportGenerator {
    fun generate(report: MutationReport, outputDir: File): File {
        val json = buildString {
            appendLine("{")
            appendLine("  \"statistics\": {")
            appendLine("    \"mutations\": ${report.totalMutations},")
            appendLine("    \"killed\": ${report.killedMutations},")
            appendLine("    \"survived\": ${report.survivedMutations}")
            appendLine("  },")
            appendLine("  \"mutations\": [")
            for ((index, result) in report.results.withIndex()) {
                appendLine("    {")
                appendLine("      \"id\": \"${result.mutation.id}\",")
                appendLine("      \"status\": \"${result.status}\",")
                appendLine("      \"operator\": \"${result.mutation.operator.operatorName}\",")
                appendLine("      \"line\": ${result.mutation.lineNumber}")
                if (index < report.results.size - 1) appendLine("    },")
                else appendLine("    }")
            }
            appendLine("  ]")
            appendLine("}")
        }
        val file = File(outputDir, "mutations.json")
        file.writeText(json)
        return file
    }
}
```

**Files:**
- `mutation-core/src/main/kotlin/.../report/XmlReportGenerator.kt`
- `mutation-core/src/main/kotlin/.../report/JsonReportGenerator.kt`
- `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt` (use new generators)

### 6.5 Readable Mutation Names

**What:** Human-readable mutation descriptions.

**Implementation:**
```kotlin
object MutationDescriber {
    fun describe(mutation: MutationInfo): String {
        return when (mutation.operator) {
            MutationOperator.CONDITIONALS_BOUNDARY -> {
                val op = when (mutation.originalOpcode) {
                    Opcodes.IF_ICMPLT -> "<"
                    Opcodes.IF_ICMPGE -> ">="
                    Opcodes.IF_ICMPGT -> ">"
                    Opcodes.IF_ICMPLE -> "<="
                    else -> "?"
                }
                val mutated = when (mutation.mutatedOpcode) {
                    Opcodes.IF_ICMPLT -> "<"
                    Opcodes.IF_ICMPGE -> ">="
                    Opcodes.IF_ICMPGT -> ">"
                    Opcodes.IF_ICMPLE -> "<="
                    else -> "?"
                }
                "Boundary: $op → $mutated"
            }
            MutationOperator.ARITHMETIC -> {
                val op = when (mutation.originalOpcode) {
                    Opcodes.IADD -> "+"
                    Opcodes.ISUB -> "-"
                    Opcodes.IMUL -> "*"
                    Opcodes.IDIV -> "/"
                    else -> "?"
                }
                val mutated = when (mutation.mutatedOpcode) {
                    Opcodes.IADD -> "+"
                    Opcodes.ISUB -> "-"
                    Opcodes.IMUL -> "*"
                    Opcodes.IDIV -> "/"
                    else -> "?"
                }
                "Arithmetic: $op → $mutated"
            }
            // ... other operators
            else -> mutation.description
        }
    }
}
```

**Files:**
- `mutation-core/src/main/kotlin/.../report/MutationDescriber.kt`

---

## Implementation Order

1. **Phase 4: Operators** (2-3 hours)
   - Add 5 new operators to MutationOperator enum
   - Implement scanner logic in Mutator.kt
   - Add unit tests for each operator
   - Update sample with new mutation points

2. **Phase 5: Suppression** (1-2 hours)
   - Create @SuppressMutations annotation
   - Implement comment-based suppression
   - Add DSL configuration options
   - Add tests

3. **Phase 6: Incremental & Reports** (2-3 hours)
   - Implement MutationHistoryManager
   - Add coverage-based skipping
   - Add timeout detection (thread + bytecode)
   - Implement XML/JSON report generators
   - Add MutationDescriber
   - Add tests

**Total estimated time:** 5-8 hours

---

## Files to Create/Modify

### New Files
- `mutation-core/src/main/kotlin/.../annotation/SuppressMutations.kt`
- `mutation-core/src/main/kotlin/.../engine/MutationHistoryManager.kt`
- `mutation-core/src/main/kotlin/.../report/XmlReportGenerator.kt`
- `mutation-core/src/main/kotlin/.../report/JsonReportGenerator.kt`
- `mutation-core/src/main/kotlin/.../report/MutationDescriber.kt`
- `mutation-core/src/test/kotlin/.../mutator/VoidMethodCallMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/IncrementMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/BooleanReturnMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/ConstructorCallMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/NonVoidMethodCallMutatorTest.kt`

### Modified Files
- `mutation-core/src/main/kotlin/.../mutator/MutationOperator.kt` (add new operators)
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt` (add scanner logic)
- `mutation-core/src/main/kotlin/.../mutator/MutationInfo.kt` (add suppression fields)
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt` (use history, timeout, coverage)
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPluginExtension.kt` (add new options)
- `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt` (use new generators)

---

## Verification

After implementation:
1. Run all tests: `./gradlew test`
2. Verify new operators generate mutations
3. Verify suppression works (annotation + comment + DSL)
4. Verify incremental analysis skips unchanged classes
5. Verify XML/JSON reports generate correctly
6. Verify timeout detection catches infinite loops
7. Verify readable mutation names in reports

---

# EXPANSION 3: Production Readiness

## Decisions (Grilled)

| Decision | Choice |
|----------|--------|
| Priority focus | All areas (full production) |
| CI/CD platform | GitHub Actions |
| Testing strategy | Full test suite (unit + integration + performance + edge cases) |
| Documentation scope | Full docs (API, architecture, contributing, changelog, migration) |
| Security measures | Full security (dependency scanning, code signing, SBOM, vulnerability reporting) |
| Performance optimization | Full optimization (caching, parallel, memory, profiling) |
| Compatibility targets | All: Multi-Kotlin, Multi-Gradle, Android, KMP |
| Developer experience | Full DX (IDE plugins, error messages, progress, validation) |
| Community features | Full community (contributing, code of conduct, issue/PR templates) |
| Implementation order | CI/CD → testing → docs → security → performance |
| Session scope | Everything (full production) |

## Phase 7: CI/CD (GitHub Actions)

### 7.1 Main CI Workflow

**File:** `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
        kotlin: [2.0.0, 2.1.10]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew test
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results-java${{ matrix.java }}-kotlin${{ matrix.kotlin }}
          path: build/reports/tests/

  build:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew build
      - uses: actions/upload-artifact@v4
        with:
          name: build-artifacts
          path: build/libs/

  code-coverage:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew jacocoTestReport
      - uses: codecov/codecov-action@v4
        with:
          file: build/reports/jacoco/test/jacocoTestReport.xml
          token: ${{ secrets.CODECOV_TOKEN }}
```

### 7.2 Publish Workflow

**File:** `.github/workflows/publish.yml`

```yaml
name: Publish

on:
  release:
    types: [created]
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to publish'
        required: true

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}
          GPG_SIGNING_PASSWORD: ${{ secrets.GPG_SIGNING_PASSWORD }}
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
```

### 7.3 Security Scanning Workflow

**File:** `.github/workflows/security.yml`

```yaml
name: Security

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 0 * * 1'  # Weekly on Monday

jobs:
  dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew dependencyCheckAnalyze

  codeql:
    runs-on: ubuntu-latest
    permissions:
      security-events: write
    steps:
      - uses: actions/checkout@v4
      - uses: github/codeql-action/init@v3
        with:
          languages: java, kotlin
      - uses: github/codeql-action/autobuild@v3
      - uses: github/codeql-action/analyze@v3
```

### 7.4 Release Workflow

**File:** `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew build
      - uses: softprops/action-gh-release@v1
        with:
          generate_release_notes: true
          files: build/libs/*
```

---

## Phase 8: Testing

### 8.1 Unit Tests for New Features

**Files to create:**
- `mutation-core/src/test/kotlin/.../mutator/VoidMethodCallMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/IncrementMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/BooleanReturnMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/ConstructorCallMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../mutator/NonVoidMethodCallMutatorTest.kt`
- `mutation-core/src/test/kotlin/.../annotation/SuppressMutationsTest.kt`
- `mutation-core/src/test/kotlin/.../engine/MutationHistoryManagerTest.kt`
- `mutation-core/src/test/kotlin/.../report/XmlReportGeneratorTest.kt`
- `mutation-core/src/test/kotlin/.../report/JsonReportGeneratorTest.kt`
- `mutation-core/src/test/kotlin/.../report/MutationDescriberTest.kt`

### 8.2 Integration Tests

**File:** `mutation-test-runner/src/test/kotlin/.../runner/FullIntegrationTest.kt`

```kotlin
class FullIntegrationTest {
    @Test
    fun `full mutation testing pipeline works end-to-end`() {
        // 1. Load class files
        // 2. Scan for mutations
        // 3. Generate mutants
        // 4. Run tests against mutants
        // 5. Generate report
        // 6. Verify results
    }

    @Test
    fun `incremental analysis skips unchanged classes`() {
        // 1. Run first analysis
        // 2. Save history
        // 3. Run second analysis without changes
        // 4. Verify cached results used
    }

    @Test
    fun `suppression works via annotation`() {
        // 1. Load class with @SuppressMutations
        // 2. Scan for mutations
        // 3. Verify suppressed mutations not generated
    }

    @Test
    fun `timeout detection catches infinite loops`() {
        // 1. Create mutant with infinite loop
        // 2. Run with timeout
        // 3. Verify timeout detected
    }
}
```

### 8.3 Performance Tests

**File:** `mutation-core/src/test/kotlin/.../performance/MutationPerformanceTest.kt`

```kotlin
class MutationPerformanceTest {
    @Test
    fun `mutation scanning scales linearly with class count`() {
        // Measure scanning time for 10, 100, 1000 classes
        // Verify linear scaling
    }

    @Test
    fun `parallel execution improves throughput`() {
        // Measure single-threaded vs multi-threaded execution
        // Verify speedup
    }

    @Test
    fun `incremental analysis reduces re-analysis time`() {
        // Measure first run vs subsequent runs
        // Verify time reduction
    }
}
```

### 8.4 Edge Case Tests

**File:** `mutation-core/src/test/kotlin/.../edge/EdgeCaseTest.kt`

```kotlin
class EdgeCaseTest {
    @Test
    fun `handles empty classes gracefully`() {
        // Empty class, no methods
    }

    @Test
    fun `handles extremely large classes`() {
        // Class with 1000+ methods
    }

    @Test
    fun `handles malformed bytecode`() {
        // Corrupted class file
    }

    @Test
    fun `handles concurrent mutation scanning`() {
        // Multiple threads scanning same class
    }
}
```

---

## Phase 9: Documentation

### 9.1 API Documentation (KDoc)

**Files to update:**
- All public classes/functions in `mutation-core`
- All public classes/functions in `mutation-test-runner`
- All public classes/functions in `mutation-gradle-plugin`

**Example:**
```kotlin
/**
 * Main mutation engine that applies mutations to class bytecode.
 *
 * This engine uses ASM to visit and transform bytecode instructions,
 * generating mutants for mutation testing analysis.
 *
 * Usage:
 * ```kotlin
 * val engine = MutationEngine(
 *     enabledOperators = MutationOperator.ALL_OPERATORS,
 *     timeoutMs = 30000,
 *     maxParallelMutants = 4
 * )
 *
 * val report = engine.runMutationTesting(
 *     classFiles = mapOf("com/example/MyClass" to classBytes),
 *     testClassNames = listOf("com.example.MyClassTest"),
 *     testClassBytes = mapOf("com/example/MyClassTest" to testBytes)
 * )
 * ```
 *
 * @param enabledOperators Set of mutation operators to apply
 * @param timeoutMs Timeout per mutant in milliseconds
 * @param maxParallelMutants Maximum number of mutants to test in parallel
 * @throws IllegalArgumentException if no operators are enabled
 * @since 0.1.0
 */
class MutationEngine(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val timeoutMs: Long = 30000,
    private val maxParallelMutants: Int = Runtime.getRuntime().availableProcessors()
) {
    // ...
}
```

### 9.2 Architecture Documentation

**File:** `docs/ARCHITECTURE.md`

```markdown
# Architecture

## Overview

The mutation testing library consists of four modules:

1. **mutation-core** - Core mutation engine
2. **mutation-test-runner** - JUnit Platform integration
3. **mutation-gradle-plugin** - Gradle plugin
4. **mutation-sample** - Example project

## Module Dependencies

```
mutation-gradle-plugin
    ↓
mutation-test-runner
    ↓
mutation-core
```

## Core Components

### Mutator
- Scans bytecode for mutation points
- Applies mutations to generate mutants
- Uses ASM for bytecode manipulation

### MutationEngine
- Orchestrates mutation testing process
- Manages parallel execution
- Handles timeout detection

### MutationHistoryManager
- Caches mutation results
- Enables incremental analysis
- Uses SHA-256 hashing for change detection

### ReportGenerators
- HTML, XML, JSON report generation
- Human-readable mutation descriptions
- Integration with CI/CD systems

## Data Flow

1. Load class files from build output
2. Scan for mutation points
3. Generate mutants
4. Run tests against each mutant
5. Collect results
6. Generate reports
7. Cache results for incremental analysis
```

### 9.3 Contributing Guide

**File:** `CONTRIBUTING.md`

```markdown
# Contributing

## Development Setup

1. Clone the repository
2. Run `./gradlew build` to verify setup
3. Run `./gradlew test` to run tests

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Add KDoc for public APIs
- Write tests for new features

## Pull Request Process

1. Create feature branch from `develop`
2. Make changes
3. Add tests
4. Update documentation
5. Submit PR

## Issue Templates

- Bug report
- Feature request
- Performance improvement
- Documentation improvement
```

### 9.4 Changelog

**File:** `CHANGELOG.md`

```markdown
# Changelog

## [0.2.0] - 2024-XX-XX

### Added
- 5 new mutation operators (VOID_METHOD_CALLS, INCREMENTS, TRUE/FALSE_RETURNS, CONSTRUCTOR_CALLS, NON_VOID_METHOD_CALLS)
- @SuppressMutations annotation
- Comment-based line suppression
- Incremental analysis with MutationHistoryManager
- XML and JSON report generators
- MutationDescriber for readable names
- GitHub Actions CI/CD workflows
- Full KDoc API documentation
- Architecture documentation
- Contributing guide
- Changelog

### Changed
- Updated Gradle plugin DSL with new configuration options
- Improved error messages
- Enhanced report formatting

### Fixed
- Fixed timeout detection for infinite loops
- Fixed incremental analysis caching

## [0.1.0] - 2024-XX-XX

### Added
- Initial release
- 7 MVP mutation operators
- 4 Kotlin-specific operators
- Gradle plugin with auto-discovery
- HTML report generation
- JUnit 5 integration
```

### 9.5 Migration Guide

**File:** `docs/MIGRATION.md`

```markdown
# Migration Guide

## From 0.1.0 to 0.2.0

### New Configuration Options

```kotlin
mutationTest {
    // New options
    failOnScoreThreshold.set(80)
    failOnCoverageThreshold.set(90)
    maxMutationsPerClass.set(100)
    enableIncrementalAnalysis.set(true)
    mutantTimeoutMs.set(10000)
}
```

### New Operators

```kotlin
mutationTest {
    enabledOperators.addAll(
        "VOID_METHOD_CALLS",
        "INCREMENTS",
        "TRUE_RETURNS",
        "FALSE_RETURNS",
        "CONSTRUCTOR_CALLS",
        "NON_VOID_METHOD_CALLS"
    )
}
```

### Suppression

```kotlin
// Annotation-based
@SuppressMutations(reason = "Generated code")
class BuildConfig { }

// Comment-based
fun calculate(): Int {
    return value + 1  // mutflow:ignore
}
```
```

---

## Phase 10: Security

### 10.1 Dependency Scanning

**File:** `build.gradle.kts` (add to root)

```kotlin
plugins {
    id("org.ow2.dependency-check") version "9.0.7"
}

dependencyCheck {
    failBuildOnCVSS = 7
    formats = ["HTML", "JSON"]
    suppressionFile = "dependency-check-suppressions.xml"
}
```

### 10.2 Code Signing

**Already implemented** in `build.gradle.kts`:
```kotlin
signing {
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["maven"])
}
```

### 10.3 SBOM Generation

**File:** `build.gradle.kts` (add to root)

```kotlin
plugins {
    id("org.cyclonedx") version "1.8.0"
}

cyclonedx {
    schemaVersion = "1.5"
    includeBom = true
    includeReport = false
}
```

### 10.4 Vulnerability Reporting

**File:** `SECURITY.md`

```markdown
# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. Email: security@example.com
2. Include: Description, steps to reproduce, potential impact
3. Response: Within 48 hours

## Security Measures

- Dependency scanning with OWASP Dependency-Check
- Code signing with GPG
- SBOM generation for supply chain security
- Regular security audits
```

---

## Phase 11: Performance Optimization

### 11.1 Caching

**Already implemented** via `MutationHistoryManager`.

### 11.2 Parallel Execution

**Already implemented** via `maxParallelMutants` parameter.

### 11.3 Memory Optimization

**File:** `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`

```kotlin
// Add memory-efficient mutant generation
private fun generateMutantsEfficiently(
    classFiles: Map<String, ByteArray>
): Map<String, List<Pair<MutationInfo, ByteArray>>> {
    return classFiles.map { (className, classBytes) ->
        val mutator = Mutator(enabledOperators)
        val mutants = mutator.generateMutants(classBytes)
        className to mutants
    }.toMap()
}
```

### 11.4 Profiling Support

**File:** `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`

```kotlin
// Add profiling metrics
data class MutationMetrics(
    val scanTimeMs: Long,
    val mutantGenerationTimeMs: Long,
    val testExecutionTimeMs: Long,
    val reportGenerationTimeMs: Long,
    val totalTimeMs: Long,
    val memoryUsedMb: Long
)
```

---

## Phase 12: Compatibility

### 12.1 Multi-Kotlin Version Support

**File:** `build.gradle.kts`

```kotlin
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "21"
            // Support multiple Kotlin versions via matrix testing
        }
    }
}
```

### 12.2 Multi-Gradle Version Support

**File:** `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
```

**CI Matrix:**
```yaml
strategy:
  matrix:
    gradle: [8.0, 8.5, 8.10]
```

### 12.3 Android Support

**Already supported** via Gradle plugin.

### 12.4 KMP Support

**Already supported** via Kotlin Multiplatform.

---

## Phase 13: Developer Experience

### 13.1 Better Error Messages

**File:** `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`

```kotlin
// Add descriptive error messages
throw IllegalArgumentException(
    "No mutation operators enabled. " +
    "Add operators to mutationTest { enabledOperators.addAll(...) } " +
    "or use MutationOperator.ALL_OPERATORS"
)
```

### 13.2 Progress Reporting

**File:** `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt`

```kotlin
// Add progress bar
val progress = ProgressLogger(project, "Mutation Testing")
progress.start("Analyzing mutations...")

for ((index, mutation) in mutations.withIndex()) {
    progress.progress("Testing mutation ${index + 1}/${mutations.size}: ${mutation.description}")
    // ... test mutation
}

progress.completed()
```

### 13.3 Configuration Validation

**File:** `mutation-gradle-plugin/src/main/kotlin/.../MutationPlugin.kt`

```kotlin
// Validate configuration
project.afterEvaluate {
    if (extension.enabledOperators.get().isEmpty()) {
        project.logger.warn("No mutation operators enabled. Using defaults.")
        extension.enabledOperators.set(MutationOperator.MVP_OPERATORS.map { it.operatorName }.toSet())
    }

    if (extension.timeoutMs.get() <= 0) {
        throw IllegalArgumentException("Timeout must be positive")
    }
}
```

---

## Phase 14: Community Features

### 14.1 Issue Templates

**File:** `.github/ISSUE_TEMPLATE/bug_report.md`

```markdown
---
name: Bug report
about: Create a report to help us improve
title: ''
labels: bug
assignees: ''
---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Run '...'
2. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Environment**
- OS: [e.g., Ubuntu 22.04]
- Java version: [e.g., 21]
- Kotlin version: [e.g., 2.1.10]
- Gradle version: [e.g., 8.10]
- Plugin version: [e.g., 0.2.0]
```

### 14.2 PR Template

**File:** `.github/pull_request_template.md`

```markdown
## Description

Brief description of changes

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist

- [ ] Code follows project style
- [ ] Self-reviewed code
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No breaking changes
```

### 14.3 Code of Conduct

**File:** `CODE_OF_CONDUCT.md`

```markdown
# Contributor Covenant Code of Conduct

## Our Pledge

We as members, contributors, and leaders pledge to make participation in our
community a harassment-free experience for everyone, regardless of age, body
size, visible or invisible disability, ethnicity, sex characteristics, gender
identity and expression, level of experience, education, socio-economic status,
nationality, personal appearance, race, religion, or sexual identity
and orientation.

## Our Standards

Examples of behavior that contributes to a positive environment:

* Using welcoming and inclusive language
* Being respectful of differing viewpoints and experiences
* Gracefully accepting constructive criticism
* Focusing on what is best for the community
* Showing empathy towards other community members

Examples of unacceptable behavior:

* The use of sexualized language or imagery, and sexual attention or
  advances of any kind
* Trolling, insulting or derogatory comments, and personal or political attacks
* Public or private harassment
* Publishing others' private information without explicit permission
* Other conduct which could reasonably be considered inappropriate in a
  professional setting

## Enforcement Responsibilities

Community leaders are responsible for clarifying and enforcing our standards of
acceptable behavior and will take appropriate and fair corrective action in
response to any behavior that they deem inappropriate, threatening, offensive,
or harmful.

## Scope

This Code of Conduct applies within all community spaces, and also applies when
an individual is officially representing the community in public spaces.

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported to the community leaders responsible for enforcement at
[INSERT CONTACT METHOD].

All complaints will be reviewed and investigated promptly and fairly.

All community leaders are obligated to respect the privacy and security of the
reporter of any incident.

## Enforcement Guidelines

Community leaders will follow these Community Impact Guidelines in determining
the consequences for any action they deem in violation of this Code of Conduct:

### 1. Correction

**Community Impact**: Use of inappropriate language or other behavior deemed
unprofessional or unwelcome in the community.

**Consequence**: A private, written warning from community leaders, providing
clarity around the nature of the violation and an explanation of why the
behavior was inappropriate. A public apology may be requested.

### 2. Warning

**Community Impact**: A violation through a single incident or series
of actions.

**Consequence**: A warning with consequences for continued behavior. No
interaction with the people involved, including unsolicited interaction with
those enforcing the Code of Conduct, for a specified period of time. This
includes avoiding interactions in community spaces as well as external channels
like social media. Violating these terms may lead to a temporary or
permanent ban.

### 3. Temporary Ban

**Community Impact**: A serious violation of community standards, including
sustained inappropriate behavior.

**Consequence**: A temporary ban from any sort of interaction or public
communication with the community for a specified period of time. No public or
private interaction with the people involved, including unsolicited interaction
with those enforcing the Code of Conduct, is allowed during this period.
Violating these terms may lead to a permanent ban.

### 4. Permanent Ban

**Community Impact**: Demonstrating a pattern of violation of community
standards, including sustained inappropriate behavior, harassment of an
individual, or aggression toward or disparagement of classes of individuals.

**Consequence**: A permanent ban from any sort of public interaction within
the community.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant][homepage], version 2.0,
available at https://www.contributor-covenant.org/version/2/0/code_of_conduct.html.

Community Impact Guidelines were inspired by [Mozilla's code of conduct
enforcement ladder](https://github.com/mozilla/diversity).

[homepage]: https://www.contributor-covenant.org

For answers to common questions about this code of conduct, see the FAQ at
https://www.contributor-covenant.org/faq. Translations are available at
https://www.contributor-covenant.org/translations.
```

---

## Implementation Order

1. **Phase 7: CI/CD** (2-3 hours)
   - Create GitHub Actions workflows
   - Set up dependency scanning
   - Configure code signing

2. **Phase 8: Testing** (3-4 hours)
   - Write unit tests for new features
   - Write integration tests
   - Write performance tests
   - Write edge case tests

3. **Phase 9: Documentation** (2-3 hours)
   - Add KDoc to all public APIs
   - Write architecture documentation
   - Write contributing guide
   - Write changelog
   - Write migration guide

4. **Phase 10: Security** (1-2 hours)
   - Add dependency scanning
   - Add SBOM generation
   - Write security policy

5. **Phase 11: Performance** (1-2 hours)
   - Add memory optimization
   - Add profiling support
   - Optimize parallel execution

6. **Phase 12: Compatibility** (1-2 hours)
   - Test multi-Kotlin version support
   - Test multi-Gradle version support
   - Verify Android support
   - Verify KMP support

7. **Phase 13: Developer Experience** (1-2 hours)
   - Improve error messages
   - Add progress reporting
   - Add configuration validation

8. **Phase 14: Community** (1-2 hours)
   - Create issue templates
   - Create PR template
   - Write code of conduct

**Total estimated time:** 12-18 hours

---

## Files to Create/Modify

### New Files
- `.github/workflows/ci.yml`
- `.github/workflows/publish.yml`
- `.github/workflows/security.yml`
- `.github/workflows/release.yml`
- `.github/ISSUE_TEMPLATE/bug_report.md`
- `.github/ISSUE_TEMPLATE/feature_request.md`
- `.github/pull_request_template.md`
- `docs/ARCHITECTURE.md`
- `docs/MIGRATION.md`
- `CONTRIBUTING.md`
- `CHANGELOG.md`
- `SECURITY.md`
- `CODE_OF_CONDUCT.md`
- `dependency-check-suppressions.xml`

### Modified Files
- `build.gradle.kts` (add dependency-check, cyclonedx plugins)
- All source files (add KDoc)
- `mutation-gradle-plugin/src/main/kotlin/.../MutationTask.kt` (add progress reporting)
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPlugin.kt` (add validation)
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt` (add metrics, error messages)

---

## Verification

After implementation:
1. Run all tests: `./gradlew test`
2. Run dependency check: `./gradlew dependencyCheckAnalyze`
3. Generate SBOM: `./gradlew cyclonedx`
4. Verify CI workflows work
5. Verify documentation renders correctly
6. Verify security scanning passes
7. Verify performance benchmarks

---

# EXPANSION 4: Speed, Usability, Reports

## Decisions (Grilled)

| Decision | Choice |
|----------|--------|
| Subsumption | Full subsumption (check if M1 killed by all tests that kill M2) |
| Weak mutants | Coverage-based (check if mutation reaches execution point) |
| Inlined finally | Bytecode analysis (ASM LineNumberNode analysis) |
| Regex filtering | Regex patterns (targetClasses + targetTests) |
| Test ordering | History-based (run tests that killed most mutants first) |
| History tracking | Per-commit tracking (store scores per commit SHA) |
| CSV report | Full CSV (all mutation details) |
| Mutation graph | Interactive HTML (test-mutant relationships) |
| Class-level scores | Per-class breakdown in reports |
| Custom mutators | Public interface (SPI-style) |
| Implementation order | Speed → Usability → Reports |

---

## Priority 1: Speed Features

### P1.1 Subsumption Detection

**What:** Skip mutant M2 if all tests that kill M2 also kill M1. M1 subsumes M2.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`
- `mutation-core/src/main/kotlin/.../model/Mutation.kt` (add subsumption tracking)
- `mutation-core/src/main/kotlin/.../report/SubsumptionAnalyzer.kt` (NEW)

**Algorithm:**
```
1. Run all mutants against all tests
2. Build kill matrix: Map<MutationId, Set<TestName>>
3. For each pair (M1, M2):
   - If kill(M1) ⊆ kill(M2): M1 subsumes M2
   - Skip M2
4. Report subsumed mutants as "subsumed" (not killed/survived)
```

**Data model:**
```kotlin
data class SubsumptionResult(
    val subsumedMutations: List<Mutation>,
    val subsumedBy: Map<String, String>, // M2 -> M1 (M1 subsumes M2)
    val skippedCount: Int
)
```

**UI/Reporting:**
- Console: "Subsumed 45 mutants (23% of total)"
- HTML report: "Subsumed" section with details
- CSV: Add "subsumed_by" column

### P1.2 Weak Mutant Detection

**What:** Check if mutation actually reaches execution point before running full test.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`
- `mutation-core/src/main/kotlin/.../engine/WeakMutationAnalyzer.kt` (NEW)
- `mutation-core/src/main/kotlin/.../model/Mutation.kt` (add weak status)

**Algorithm:**
```
1. For each mutant M:
   - Get line number from MutationInfo
   - Check coverage data: is this line covered by any test?
   - If line not covered: mark as "weak" (never reached)
2. Only run tests against non-weak mutants
3. Report weak mutants separately
```

**Data model:**
```kotlin
enum class MutationStrength {
    STRONG,   // Reaches execution point
    WEAK      // Never reaches execution point
}

data class Mutation(
    // ... existing fields
    val strength: MutationStrength = MutationStrength.STRONG
)
```

**UI/Reporting:**
- Console: "Weak mutants: 12 (7% of total)"
- HTML report: "Weak Mutants" section
- CSV: Add "strength" column

### P1.3 Inlined Finally Detection

**What:** Detect finally blocks inlined by compiler. Create single mutation, not per-line.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt`
- `mutation-core/src/main/kotlin/.../mutator/InlinedFinallyDetector.kt` (NEW)

**Algorithm:**
```
1. Scan for try-finally blocks in bytecode
2. Detect finally block inlining:
   - Look for duplicate bytecode sequences
   - Check LineNumberNode patterns
3. Create single mutation for entire finally block
4. Skip individual mutations within inlined block
```

**ASM analysis:**
```kotlin
class InlinedFinallyDetector {
    fun detect(classBytes: ByteArray): List<InlinedFinallyBlock> {
        // Analyze MethodNode's tryCatchBlocks
        // Detect finally handlers (type = null)
        // Find duplicate bytecode patterns
        // Return list of inlined blocks
    }
}
```

**UI/Reporting:**
- Console: "Inlined finally blocks: 3 (skipped 9 mutations)"
- HTML report: "Inlined Code" section
- CSV: Add "inlined" column

---

## Priority 2: Usability Features

### P2.1 Regex Filtering

**What:** Support regex patterns for class/test filtering.

**Files to modify:**
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPluginExtension.kt`
- `mutation-core/src/main/kotlin/.../filter/ClassFilter.kt` (NEW)

**DSL:**
```kotlin
mutationTest {
    // Regex patterns
    targetClasses.set(listOf("com\\.example\\..*"))
    targetTests.set(listOf("com\\.example\\..*Test"))
    
    // Exclude patterns
    excludeClasses.set(listOf("com\\.example\\.generated\\..*"))
    excludeTests.set(listOf("com\\.example\\.Generated.*"))
}
```

**Implementation:**
```kotlin
class ClassFilter(
    private val targetPatterns: List<Regex>,
    private val excludePatterns: List<Regex>
) {
    fun shouldMutate(className: String): Boolean {
        val matchesTarget = targetPatterns.isEmpty() || 
            targetPatterns.any { it.matches(className) }
        val matchesExclude = excludePatterns.any { it.matches(className) }
        return matchesTarget && !matchesExclude
    }
}
```

### P2.2 Test Ordering

**What:** Run tests that killed most mutants first.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`
- `mutation-core/src/main/kotlin/.../engine/TestOrderingStrategy.kt` (NEW)

**Algorithm:**
```
1. Load history: Map<TestId, KillCount>
2. Sort tests by kill count descending
3. Run top-k tests first (k = 10% of tests, min 5)
4. If mutant killed by early tests: skip remaining tests
5. Update history with new kill counts
```

**Data model:**
```kotlin
data class TestKillCount(
    val testId: String,
    val killCount: Int,
    val lastRun: Long
)

class TestOrderingStrategy(
    private val historyManager: MutationHistoryManager
) {
    fun orderTests(testIds: List<String>): List<String> {
        val history = historyManager.loadTestHistory()
        return testIds.sortedByDescending { 
            history[it]?.killCount ?: 0 
        }
    }
}
```

### P2.3 Per-Commit History Tracking

**What:** Store mutation scores per commit SHA.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../engine/MutationHistoryManager.kt`

**Data model:**
```kotlin
data class CommitHistory(
    val commitSha: String,
    val timestamp: Long,
    val mutationScore: Int,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val weakMutations: Int,
    val subsumedMutations: Int
)
```

**Storage:**
```
.mutation-history/
├── history.json          # Current run
├── commits/
│   ├── abc123.json       # Per-commit history
│   ├── def456.json
│   └── ...
└── trends.json           # Aggregated trends
```

---

## Priority 3: Report Features

### P3.1 Full CSV Report

**What:** CSV with all mutation details.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../report/CsvReportGenerator.kt` (NEW)

**CSV columns:**
```
mutation_id,status,operator,operator_description,className,methodName,lineNumber,
description,executionTimeMs,strength,subsumedBy,inlined
```

**Example:**
```csv
mut_001,KILLED,CONDITIONALS_BOUNDARY,Boundary: == 0 -> != 0,com.example.Calculator,max,47,Return 0,12,STRONG,,false
mut_002,SURVIVED,ARITHMETIC,Arithmetic: + -> -,com.example.Calculator,add,32,Return -1,15,STRONG,,false
mut_003,WEAK,NEGATE_CONDITIONALS,Negate: == -> !=,com.example.Calculator,equals,28,Return true,8,WEAK,,false
mut_004,SUBSUMED,RETURN_VALS,Return 0,com.example.Calculator,min,52,Return 0,11,STRONG,mut_001,false
```

### P3.2 Interactive HTML Graph

**What:** Interactive visualization of test-mutant relationships.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../report/MutationGraphGenerator.kt` (NEW)
- `mutation-core/src/main/resources/templates/mutation-graph.html` (NEW)

**Features:**
- Force-directed graph layout
- Nodes: tests (circles) and mutants (squares)
- Edges: test kills mutant (solid), test doesn't kill mutant (dashed)
- Color: killed (green), survived (red), weak (yellow), subsumed (gray)
- Hover: show mutation details
- Click: filter by test or mutant
- Export: PNG/SVG

**Implementation:**
- Use D3.js for graph rendering
- Generate JSON data from mutation results
- Embed in HTML template

### P3.3 Per-Class Breakdown

**What:** Show mutation score per class in reports.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../model/MutationReport.kt`
- `mutation-core/src/main/kotlin/.../report/HtmlReportGenerator.kt`

**Data model:**
```kotlin
data class ClassMutationScore(
    val className: String,
    val totalMutations: Int,
    val killedMutations: Int,
    val survivedMutations: Int,
    val weakMutations: Int,
    val subsumedMutations: Int,
    val score: Int // percentage
)

data class MutationReport(
    // ... existing fields
    val classScores: List<ClassMutationScore>
)
```

**HTML report:**
```html
<div class="class-scores">
  <h2>Mutation Scores by Class</h2>
  <table>
    <tr><th>Class</th><th>Score</th><th>Killed</th><th>Survived</th><th>Weak</th><th>Subsumed</th></tr>
    <tr><td>Calculator</td><td>92%</td><td>23</td><td>2</td><td>1</td><td>4</td></tr>
    <tr><td>StringUtils</td><td>88%</td><td>15</td><td>2</td><td>0</td><td>3</td></tr>
  </table>
</div>
```

### P3.4 Custom Mutators API

**What:** Public interface for users to add operators.

**Files to modify:**
- `mutation-core/src/main/kotlin/.../mutator/CustomMutator.kt` (NEW)
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt`

**Interface:**
```kotlin
interface CustomMutator {
    val name: String
    val description: String
    
    fun canMutate(methodNode: MethodNode): Boolean
    fun generateMutations(methodNode: MethodNode): List<MutationInfo>
    fun applyMutation(methodNode: MethodNode, mutation: MutationInfo): MethodNode
}
```

**Registration:**
```kotlin
// ServiceLoader pattern
// META-INF/services/com.github.rodrigotimoteo.mutation.mutator.CustomMutator
com.example.MyCustomMutator

// Or Gradle DSL
mutationTest {
    customOperators.addAll(
        "com.example.MyCustomMutator"
    )
}
```

---

## Implementation Order

1. **Priority 1: Speed** (4-6 hours)
   - P1.1 Subsumption detection
   - P1.2 Weak mutant detection
   - P1.3 Inlined finally detection

2. **Priority 2: Usability** (3-4 hours)
   - P2.1 Regex filtering
   - P2.2 Test ordering
   - P2.3 Per-commit history tracking

3. **Priority 3: Reports** (3-4 hours)
   - P3.1 Full CSV report
   - P3.2 Interactive HTML graph
   - P3.3 Per-class breakdown
   - P3.4 Custom mutators API

**Total estimated time:** 10-14 hours

---

## Files to Create/Modify

### New Files
- `mutation-core/src/main/kotlin/.../report/SubsumptionAnalyzer.kt`
- `mutation-core/src/main/kotlin/.../engine/WeakMutationAnalyzer.kt`
- `mutation-core/src/main/kotlin/.../mutator/InlinedFinallyDetector.kt`
- `mutation-core/src/main/kotlin/.../filter/ClassFilter.kt`
- `mutation-core/src/main/kotlin/.../engine/TestOrderingStrategy.kt`
- `mutation-core/src/main/kotlin/.../report/CsvReportGenerator.kt`
- `mutation-core/src/main/kotlin/.../report/MutationGraphGenerator.kt`
- `mutation-core/src/main/resources/templates/mutation-graph.html`
- `mutation-core/src/main/kotlin/.../mutator/CustomMutator.kt`

### Modified Files
- `mutation-core/src/main/kotlin/.../engine/MutationEngine.kt`
- `mutation-core/src/main/kotlin/.../model/Mutation.kt`
- `mutation-core/src/main/kotlin/.../model/MutationReport.kt`
- `mutation-core/src/main/kotlin/.../mutator/Mutator.kt`
- `mutation-core/src/main/kotlin/.../engine/MutationHistoryManager.kt`
- `mutation-core/src/main/kotlin/.../report/HtmlReportGenerator.kt`
- `mutation-gradle-plugin/src/main/kotlin/.../MutationPluginExtension.kt`

---

## Verification

After implementation:
1. Run all tests: `./gradlew test`
2. Verify subsumption skips redundant mutants
3. Verify weak mutants detected correctly
4. Verify inlined finally blocks detected
5. Verify regex filtering works
6. Verify test ordering improves speed
7. Verify history tracks per commit
8. Verify CSV report generates correctly
9. Verify HTML graph renders correctly
10. Verify per-class scores in reports
11. Verify custom mutators can be registered

---

# EXPANSION 5: MutFlow-Inspired Features

## Decisions (Grilled)

| Decision | Choice |
|----------|--------|
| JUnit extension | Hybrid (ASM + JUnit extension for multi-run) |
| Verification modes | STRICT, LENIENT, DISABLED |
| Timeout detection | Inject timeout checks in loops |
| Partial run detection | Skip when running single tests from IDE |
| Architecture | Keep bytecode approach, add JUnit extension |

---

## Feature 1: JUnit Extension (Compile Once)

**What:** Run tests multiple times automatically, one per mutation. No separate process.

**How it works:**
1. User annotates test class with `@MutKtTest`
2. JUnit extension runs baseline test first
3. Extension discovers mutation points from bytecode
4. Extension runs tests N times (one per mutation)
5. Each run activates one mutation via thread-local flag
6. If test passes with mutation active → mutation survived → fail

**Files to create/modify:**
- `mutation-core/src/main/kotlin/.../extension/MutKtTest.kt` (annotation)
- `mutation-core/src/main/kotlin/.../extension/MutKtExtension.kt` (JUnit extension)
- `mutation-core/src/main/kotlin/.../engine/MutationRegistry.kt` (thread-local mutation state)
- `mutation-core/src/main/kotlin/.../engine/MutantClassLoader.kt` (add mutation activation)

**API:**
```kotlin
@MutKtTest
class CalculatorTest {
    private val calc = Calculator()

    @Test
    fun `add should return sum`() {
        val result = MutKt.underTest { calc.add(2, 3) }
        assertEquals(5, result)
    }
}
```

**Key classes:**
```kotlin
@Target(AnnotationTarget.CLASS)
annotation class MutKtTest(
    val maxMutations: Int = 0,  // 0 = all
    val timeout: Long = 30000,
    val verification: VerificationMode = VerificationMode.STRICT
)

enum class VerificationMode {
    STRICT,    // Survivors fail build
    LENIENT,   // Survivors reported but don't fail
    DISABLED   // Skip mutation testing
}

object MutKt {
    fun <T> underTest(block: () -> T): T {
        val mutation = MutationRegistry.currentMutation
        return if (mutation != null) {
            // Run with mutation active
            block()
        } else {
            // Baseline run
            block()
        }
    }
}
```

---

## Feature 2: Verification Modes

**What:** Control how surviving mutations are handled.

**Implementation:**
```kotlin
enum class VerificationMode {
    STRICT,    // Default: survivors fail build
    LENIENT,   // Survivors reported but don't fail
    DISABLED   // Skip mutation testing entirely
}
```

**Configuration:**
```kotlin
@MutKtTest(verification = VerificationMode.LENIENT)
class CalculatorTest { ... }

// Or via environment variable
// MUKT_VERIFICATION_MODE=LENIENT
```

---

## Feature 3: Timeout Detection

**What:** Detect mutations that cause infinite loops.

**How it works:**
1. MutantClassLoader injects timeout check at top of loop bodies
2. Check calls `MutationRegistry.checkTimeout()`
3. If timeout exceeded → throw `MutationTimedOutException`
4. Test fails with actionable guidance

**Files to modify:**
- `mutation-core/src/main/kotlin/.../mutator/MutantClassLoader.kt`
- `mutation-core/src/main/kotlin/.../engine/MutationRegistry.kt`

**Implementation:**
```kotlin
// In MutantClassLoader, when generating mutant bytecode:
// Add at top of loop body:
//   if (System.currentTimeMillis() - startTime > timeout) {
//       throw MutationTimedOutException("Mutation caused infinite loop at line $lineNumber")
//   }
```

---

## Feature 4: Partial Run Detection

**What:** Skip mutation testing when running single tests from IDE.

**How it works:**
1. Detect if running in IDE (check for `idea` in classpath)
2. Detect if running single test class (check test count)
3. If both true → skip mutation runs, only run baseline

**Files to modify:**
- `mutation-core/src/main/kotlin/.../extension/MutKtExtension.kt`

---

## Feature 5: Mutation Registry (Thread-Local State)

**What:** Track which mutation is active for current test run.

**Files to create:**
- `mutation-core/src/main/kotlin/.../engine/MutationRegistry.kt`

**Implementation:**
```kotlin
object MutationRegistry {
    private val currentMutation = ThreadLocal<MutationInfo?>()
    private val startTime = ThreadLocal<Long>()
    
    fun currentMutation(): MutationInfo? = currentMutation.get()
    
    fun startMutation(mutation: MutationInfo, timeout: Long) {
        currentMutation.set(mutation)
        startTime.set(System.currentTimeMillis())
    }
    
    fun endMutation() {
        currentMutation.remove()
        startTime.remove()
    }
    
    fun checkTimeout() {
        val start = startTime.get() ?: return
        val elapsed = System.currentTimeMillis() - start
        // Timeout check handled by MutantClassLoader injection
    }
}
```

---

## Implementation Order

1. **MutationRegistry** (1h) - Thread-local state for active mutation
2. **MutKtTest annotation** (30min) - JUnit 5 annotation
3. **MutKtExtension** (2h) - JUnit extension for multi-run
4. **MutKt.underTest** (30min) - API for explicit test scoping
5. **Timeout detection** (1h) - Inject timeout checks in loops
6. **Partial run detection** (30min) - Skip in IDE
7. **Verification modes** (30min) - STRICT/LENIENT/DISABLED

**Total:** ~6 hours

---

## Files to Create

- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/extension/MutKtTest.kt`
- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/extension/MutKtExtension.kt`
- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/extension/MutKt.kt`
- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/engine/MutationRegistry.kt`

## Files to Modify

- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/classloader/MutantClassLoader.kt`
- `mutation-core/src/main/kotlin/com/github/rodrigotimoteo/mutation/engine/MutationEngine.kt`
- `mutation-gradle-plugin/src/main/kotlin/com/github/rodrigotimoteo/mutation/gradle/MutationPluginExtension.kt`

---

## Verification

After implementation:
1. Run all tests: `./gradlew test`
2. Create sample test with `@MutKtTest`
3. Verify baseline run works
4. Verify mutation runs activate mutations
5. Verify timeout detection catches infinite loops
6. Verify LENIENT mode doesn't fail build
7. Verify DISABLED mode skips mutations
8. Verify partial run detection works in IDE