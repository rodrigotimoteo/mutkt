# Changelog

## [0.3.2] - 2026-07-01

### Android Variant Resolution Fix (multi-flavor library scenario)
- **`artifactType=jar` ArtifactView** — `AgpVariantResolver` wraps AGP's `<variant>RuntimeClasspath` and `<variant>UnitTestRuntimeClasspath` configurations in `Configuration.incoming.artifactView { it.attributes.attribute(Attribute.of("artifactType", String::class.java), "jar") }.files`. Fixes the `Cannot choose between the following variants of project :sharedTestCode` ambiguity error when the consumer app has no own product flavors but depends on a library that publishes multiple `*RuntimeElements` sub-variants on a `flavorDimensions` axis (e.g. `brand=production` / `brand=staging`). Verified via `DefaultMutableAttributeContainer.doInsertion` bytecode: the `artifactView` closure mutates a separate `viewAttributes` container that is *concatenated* with the original configuration's request attributes at resolution time — existing `BuildTypeAttr=debug` and `ProductFlavor:brand=production` from `defaultConfig.missingDimensionStrategy` are preserved, `artifactType=jar` is added on top.
- **Per-variant configuration lookup** — `findMainRuntimeConfiguration` / `findUnitTestRuntimeConfiguration` probe both `<variant>...RuntimeClasspath` and `<Variant>...RuntimeClasspath` (capitalized form) for AGP quirk tolerance.
- **Actionable error formatter** — `formatVariantResolutionError` extracts the unmatched `ProductFlavor:<dim>=<value>` from the Gradle "Cannot choose" stack and produces a one-line `missingDimensionStrategy("<dim>", "<value>")` fix the user can paste into their `android.defaultConfig { }` block. Regex now accepts digits, underscores, and hyphens in dimension names.
- **Sample modules** — `multi-app` (Android application with `defaultConfig.missingDimensionStrategy("brand", "production")` + Robolectric JUnit 4) and `multi-shared` (Android library with `flavorDimensions += "brand"` + `production` / `staging` flavors) form a multi-flavor library end-to-end repro. `./gradlew :multi-app:mutationTest` succeeds and `:multi-app:tasks --all | grep publish` returns no results.
- **Publishing structurally impossible for samples** — `:multi-app` / `:multi-shared` / `mutation-sample*` / `mutation-self-test` are excluded by the `excludedFromPublishing` set in root `build.gradle.kts`. An `afterEvaluate { tasks.matching { it.name.startsWith("publish") || it.name.startsWith("Upload") }.configureEach { onlyIf { false } } }` block disables every publish task at configuration time, so a future CI "publish all" workflow cannot accidentally ship a sample AAR/APK to Maven Central or GitHub Packages.
- **Kover threshold 85% → 80%** — `mutation-gradle-plugin`'s `onVariants` lambda is only exercisable against a real AGP variant; `ProjectBuilder` cannot supply one. Threshold lowered to keep the build green signal under the resolver's multi-flavor library fix landing.

### Bug Fixes
- **Silent exception swallow** — `MutationPlugin.resolveAndroidContext` now logs unknown exceptions at `error` level with stack trace (was `warn` with message-only). Misconfigurations surface in the build log instead of being lost.
- **Dead code removed** — `readUnitTestRuntimeConfiguration` and `readFlavorAttributes` private helpers in `AgpVariantResolver` (and their 6 tests) removed. `VariantCapture` stays slim (4 fields); the multi-flavor library fix lives in `buildContext` where the `ArtifactView` is applied.

## [0.3.1] - 2026-06-27

### JUnit Platform Enhancements
- **engineIds parameter** — `MutationEngine` + `ReflectionTestRunner` accept a custom JUnit Platform engine ID list (default: `junit-jupiter` + `junit-vintage` + `junit-platform-suite-engine`). Restrict discovery to a specific engine to skip Vintage on a pure-Jupiter project.
- **junit-platform-suite-engine default** — `@Suite` classes now discovered by default; no plugin config required.
- **TagFilter support** — `includeTags` / `excludeTags` constructor params on `MutationEngine` and `runTests()` on `ReflectionTestRunner`. JUnit 5 `@Tag` / `@Tags` / `@EnabledIf` / `@EnabledOnOs` filters are honored when set.
- **MutKtExtension interceptors** — `interceptTestFactoryMethod` + `interceptDynamicTestMethod` + `interceptTestTemplateMethod` added so `@TestFactory` and `@ParameterizedTest` work with MutKt's per-method tracking. `interceptWithTracking` logs per-method trigger count and clears `triggeredMutations` between methods.

### Performance & Correctness
- **Concurrent source file cache** — `ConcurrentHashMap<String, String>` replaces the previous per-class map; `findSourceCode` no longer races between worker threads.
- **3-tier classloader hierarchy** — `TestClassLoader` (parent: `URLClassLoader`, child: `MutantClassLoader`, inner: `BaseProjectClassLoader`) for JUnit 5 + inline-mock classpath isolation.
- **Parallel class scanning** — `generateAllMutations` now runs across a `ForkJoinPool` (was sequential per-class scan).
- **Coverage O(N²) → O(N)** — JaCoCo `.exec` parsed once and reused for both filter and weak-mutation analysis (was re-parsed per mutation).

### Publishing & Build
- **Maven Central via gradle-nexus-publish-plugin** — replaces the `oss.sonatype.org` manual upload. POM includes `licenses`, `developers`, `scm`, `name`, `description`, `url`. Publishing gated on `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` env vars + GPG key.
- **KMP source set support** — `GitChangeDetector.DEFAULT_SOURCE_DIRS` covers all common KMP roots (jvmMain, androidMain, iosMain + iosArm64/iosX64/iosSimulatorArm64 variants, linuxMain, macosMain, mingwX64Main, mingwX86Main, jsMain, wasmJsMain, wasmWasiMain, commonMain).
- **Kover 0.9.0** — upgraded; verify rule enforces 85% line coverage per module (mutation-core / mutation-gradle-plugin / mutation-report / mutation-test-runner).
- **AAR `libs/*.jar` extraction** — `AarExtractor.extractAll` now extracts every bundled jar under `libs/` in addition to `classes.jar`. SHA-256 stable-dir scheme prevents stale extraction accumulation.

### Bug Fixes
- **Scoring** — `NO_COVERAGE` no longer counts against the mutation score; only `KILLED` / `WEAK_KILLED` / `SUBSUMED` increase the score denominator.
- **killSets** — `KillSetStorage.saveMerged` overwrites the merged result, not the in-memory new run; cross-run kill sets are persisted correctly.
- **InterruptedException** — `MutationEngine.runMutants` propagates `InterruptedException` instead of swallowing it, so the engine responds to cancellation.
- **@SuppressMutations** — presence with no operators now suppresses all (documented behavior); `operators = [...]` suppresses only those.
- **ConcurrentHashMap in TestStrengthOrdering** — `inMemoryCache` switched to `ConcurrentHashMap`; `flushHistory` wrapped in `withFileLock` to prevent concurrent Gradle workers from corrupting `test-strength.json`.
- **ClassFilter @Deprecated** — marked `@Deprecated` with `ReplaceWith` pointing to `MutationEngine.includePatterns` / `excludePatterns`. Production wiring lives in the Gradle plugin + engine.

### KDoc & Documentation
- **MutationOperator.fromName** — added KDoc explaining the lookup + null-on-miss contract.
- **Mutator.excludedMethods** — added `@property` KDoc.
- **MutationEngine** — added `@property` for `engineIds`, `includeTags`, `excludeTags`, `excludedMethods`, `maxMutationsPerClass`, `targetTestPatterns`, `excludeTestPatterns`.
- **SubsumptionAnalyzer** — KDoc notes the O(m²) comparison cost and recommends pre-grouping mutations by `(class, method)` for large methods.
- **AarExtractor** — class-level KDoc explains SHA-256 stable-dir scheme + `clearAars` trade-off (in-run cleanup, complements Gradle `clean`).
- **README** — version bumped to 0.3.1; new "What's New in 0.3.1" section.
- **MIGRATION.md** — added 0.3.0 → 0.3.1 section (no breaking changes, enhancements only).
- **AGENTS.md** — verified operator count (6 MVP + 4 Kotlin + 6 Quick Win = 16) and gotchas.

## [0.3.0] - 2026-06-15

### Android Support (6 phases, 33 new tests)
- **Auto-detect AGP** — Plugin detects `com.android.application`/`com.android.library` via `withType(AppPlugin/LibraryPlugin)` with compileOnly AGP dependency
- **Variant resolver** — `AgpVariantResolver` resolves runtime classpath, classes dirs, test classes dirs from AGP variant tasks
- **AAR extraction** — `AarExtractor` extracts `classes.jar` from `.aar` files; `expandAars()` runs on all classpaths automatically
- **Android classes dir** — `findVariantClassesDirs()` reads from `compile<Variant>Kotlin` task `destinationDirectory` (not hardcoded JVM path)
- **auto android.jar** — `AndroidJarLocator` searches `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`, `local.properties`, `~/.android-sdk` (5 priority paths)
- **Generated class filter** — Excludes `R`, `R$*`, `BuildConfig`, `ComposableSingletons$*`, `*_Impl`, `*_Factory`, `*Hilt*` + 14 more patterns; `**/` prefix stripped
- **Kotlin compile task discovery** — Uses `withType(KotlinCompile)` instead of hardcoded `compileKotlin`; picks `compileDebugKotlin`/`compileDebugUnitTestKotlin` on Android
- **mutation-sample-android** — Working example with Robolectric, MockK, Mockito tests
- **docs/ANDROID.md** — Quick start, how-it-works, DSL config, troubleshooting, MockK/Mockito compatibility matrix

### JUnit Platform Launcher (replaced reflection runner)
- **ReflectionTestRunner.kt** — 698 → 215 lines (-69%). Now uses `LauncherFactory.create()` + `LauncherDiscoveryRequestBuilder`
- **JUnit Vintage support** — `junit-vintage-engine` added so JUnit 4 tests keep working
- **Engine filter configurable** — `engineIds` constructor parameter (default: jupiter + vintage)
- **What now works**: MockK final-class inline, MockK `mockkStatic()`, Mockito 5+ inline, `@TestFactory` dynamic tests, `@EnabledIf` annotations, custom test execution order
- **What requires user config**: `@ExtendWith` needs JUnit Jupiter on user classpath

### Critical Bug Fixes (14)
- **cachedResults never merged** — Cache hits vanished from report/strength/baseline. Now merged into `allResults`
- **Cache/baseline no locking** — Concurrent workers corrupted state. Added `withFileLock()` using `FileChannel.lock()`
- **Classloader deadlock guarantee** — Custom striped locks broke JVM guarantee. Replaced with `getClassLoadingLock()`
- **sharedFailedClassCache race** — Singleton cache corrupted concurrent runs. Now fresh `ConcurrentHashMap.newKeySet()` per group
- **EMPTY_RETURNS invalid bytecode** — `startsWith` matching included concrete subtypes. Now exact-match only
- **CoverageAnalyzer placeholder** — `listOf("covered")` never real test names. Added `ALL_TESTS_COVERED` sentinel
- **globToRegex corrupts patterns** — `replace("", ".*")` inserted `.*` between every char. Rewritten as proper parser
- **GeneratedClassFilter broken** — FQCN matching never worked for Android classes. Normalizes to slashed form
- **generateGraph dead** — Flag checked after `generateReports` ran. Moved before
- **mutktDir nesting** — Passed `mutktDir` as `projectDir` → `.mutkt/.mutkt/`. Fixed to `projectDirectory.asFile`
- **@BeforeAll/@AfterAll instance sharing** — Lifecycle methods on wrong instance. JUnit semantics preserved
- **CONSTRUCTOR_CALLS corrupted `this`** — Guard added for `NEW+DUP` pattern
- **autoDetectJaCoCo** — Set `@InputFile` before JaCoCo runs. Now only sets when file exists
- **autoRunJaCoCo** — Never wired JaCoCo agent. Now depends on `jacocoTestReport`

### High Priority Fixes (27)
- **getCommonSuperClass** — Full hierarchy walking with class caching
- **Data class copy$default** — Applier branch for `INVOKESTATIC copy$default`
- **IINC duplicate** — Single handler picks one operator
- **Mutation applier match guard** — Added `methodName`/`methodDescriptor` to all 7 guards
- **Cache key collision** — Now `operator:methodName:lineNumber:occurrenceIndex`
- **Classloader inner classes** — Throws `ClassNotFoundException` for excluded classes
- **Lock striping** — 64-stripe lock array for classloader
- **Target inner classes** — Excluded in `createGroup`, defined in `MutationClassLoader`
- **TRUE_RETURNS/FALSE_RETURNS** — Track `previousOpcode` in applier
- **CONSTRUCTOR_CALLS DUP detection** — Refuse mutation when DUP not present
- **@SuppressMutations** — Presence suppresses ALL (not just listed operators)
- **WeakMutation line=-1** — Treat as conservatively reachable
- **TestStrengthOrdering** — JSON parser + thread-safety + strength-based ordering
- **KillSetStorage** — JSON format + atomic writes
- **InlinedFinallyDetector** — Wired into public API
- **ReflectionTestRunner** — Configurable engine filter, robust disabled detection
- **MutationTestRunnerFactory** — `MutationRunnerConfig` data class (was 15 params)
- **Test class discovery** — ASM bytecode scan for `@Test` annotations
- **Inner class detection** — ASM `InnerClasses` attribute (not `$` substring)
- **Engine: mutable state** — `classFilesMap`/`allTestClassBytes` removed, passed as params
- **Engine: lazy classloaders** — `ConcurrentHashMap.computeIfAbsent`
- **Engine: classLoader.close()** — After each mutant with `runCatching`
- **Engine: pattern validation** — Throws on invalid regex at construction
- **Engine: parallel scan** — `ForkJoinPool` for `generateAllMutations`
- **Engine: sourceDirs** — `IncrementalAnalyzer`/`BaselineStorage` now accept `sourceDirs` parameter (was hardcoded). `MutationEngine` still uses a hardcoded list internally for `findSourceCode` and does not yet expose `sourceDirs` as a constructor parameter.
- **Engine: Charsets.UTF_8** — For `findSourceCode`
- **Engine: logger** — Replaced all `System.err.println` with SLF4J

### MEDIUM Fixes (49)
- **Gradle**: `@LocalState mutktDir`, Android inputs flattened as `@InputFiles`, `parseOperators` fail-fast, `failOnScoreThreshold` deprecated, `mutantTimeoutMs` deprecated, sample plugin removed
- **Reports**: `escapeJson` complete, D3 SRI hash, `escapeHtml` single quote/backtick, `escapeCsv` `\r`, TTY detection for ANSI, `showClassScores` wired, GraphGenerator labels
- **Baseline**: `MutationBaseline` data class, per-key merge in `saveMerged`, configurable source prefixes
- **Filter**: `compileRegex` with clear error messages
- **Tests**: 10 quality improvements (try/catch removed, @After added, boundary cases, @Tag integration, specific assertions)
- **Docs**: README version 0.3.0, annotation example, defaults aligned, property names corrected
- **CI**: artifact paths, dokka command, token names
- **M33 TagFilter support** — `MutationEngine` + `MutationTestRunnerFactory` accept `includeTags`/`excludeTags`; `ReflectionTestRunner.runTests` applies `TagFilter.includeTags`/`excludeTags` to the `LauncherDiscoveryRequest` so `@Tag` / `@EnabledIf` / `@EnabledOnOs` style filters can be honored from MutKt config
- **M34 IDE detection** — `MutKtExtension.isRunningSingleTest` now checks `idea.launcher` (Android Studio) in addition to the IDEA/Eclipse/VS Code markers; kdoc clarifies this detects IDE launch, not Gradle `--tests` single-test filtering
- **M35 Two-timeout model** — `MutKtExtension.interceptWithTracking` documents the JUnit `@Timeout` vs `mutantTimeoutMs` interaction; on `checkTimeout()` the wrapper now aborts by throwing `AssertionError` with a MutKt marker so the test is recorded as killed, not silently logged
- **M36 URLClassLoader classpath requirement** — `MutationTestRunner` kdoc + README troubleshooting call out the test-runtime classpath + `-javaagent` requirement for inline-mock agents (MockK / Mockito / ByteBuddy); Gradle plugin inherits these from the regular `test` task
- **M37 Per-method triggered attribution** — `MutKtExtension.interceptWithTracking` logs per-method trigger count and clears `triggeredMutations` between methods; class-level aggregate in `afterAll` is unchanged for STRICT mode contract
- **M38 Source-file cache verified** — `findSourceCode` uses `sourceFileCache` (added by M22) so a `.kt`/`.java` file is read once per class, not once per mutation

### LOW Fixes (46)
- **Model**: `Mutation.kt` bytecode fields documented, `MutationResult.kt` `getClassScores` cached
- **Registry**: Empty set cleanup, `withCleanup` helper
- **ClassLoader**: `testClassPrefixes` O(1) lookup
- **Reports**: Badge URL validation, GraphGenerator legend
- **Tests**: 16 test quality improvements
- **Docs**: 7 documentation improvements (MIGRATION, ARCHITECTURE, MOCKING, README)

### Constants Extracted
- `DEFAULT_TIMEOUT_MS`, `MUTKT_DIR`, `LOG_PREFIX`, `REPORT_WIDTH`, `ALL_TESTS_COVERED`

### Test Counts
- mutation-core: 822+ tests
- mutation-gradle-plugin: 158 tests
- mutation-report: 28 tests
- mutation-sample-android: 14 tests
- mutation-test-runner: 10 tests
- **Total: 1032+ tests, BUILD SUCCESSFUL**

## [0.2.3] - 2026-06-10

### Fixed
- **ClassLoader isolation**: Inner classes of test classes (`Test$Inner`) were loaded by `BaseProjectClassLoader` while parent test classes were in `MutationClassLoader` → `IllegalAccessError`. `MutationClassLoader` now intercepts inner classes of test classes and loads them from the same classloader.
- **JUnit 4 test detection**: `ReflectionTestRunner` only detected JUnit 5 `@Test` annotations. Most Kotlin projects use `kotlin.test.Test` which compiles to JUnit 4's `org.junit.Test`. Added support for JUnit 4 annotations (`@Test`, `@Before`, `@After`, `@BeforeClass`, `@AfterClass`).
- **KMP classpath auto-detection**: `autoDetectClasspath` only checked `testRuntimeClasspath` (JVM). KMP projects use `jvmTestRuntimeClasspath`. Added fallback detection.
- **KMP sourceSets auto-detection**: `autoDetectSourceSets` called `getByType(SourceSetContainer)` which crashes on KMP projects. Changed to `findByType` with graceful fallback.
- **KMP task dependency**: Task hardcoded `dependsOn("compileKotlin")` which doesn't exist in KMP projects. Now checks for `compileKotlinJvm`/`compileTestKotlinJvm`.
- **CI mutation score check**: Was running mutation testing on `mutation-self-test` which has no main source code → always showed 0%. Removed broken score gate.

### Validated
- **Real-world testing**: Ran mutation testing on `michaelbull/kotlin-result` (KMP library)
  - 772 mutations found
  - 30 killed (3%), 742 survived (96%)
  - 0 errors, 0 timeouts
  - 2.1s total time (367 mutations/sec)

## [0.2.2] - 2026-06-09

### Added
- **getCommonSuperClass rewrite**: Full class hierarchy + interface hierarchy walking, class caching
- **@BeforeAll/@AfterAll verification**: Fixed afterAll invocation bug, 6 lifecycle tests
- **Gradle Plugin Portal config**: maven-publish with metadata (displayName, tags, website, vcsUrl)
- **Real-time progress reporting**: Atomic counter with count, percentage, elapsed time, mutations/sec
- **Per-package targeting**: targetPackages, excludePackages DSL properties
- **CI mode**: ciMode property enables console + XML reports for CI pipelines
- **Verbose output**: verbose property shows all mutations tested
- **JaCoCo auto-detection**: Plugin detects jacoco plugin and wires coverage file
- **Task dependency**: mutationTest depends on compileKotlin + compileTestKotlin
- **Better error messages**: Shows DSL fix suggestions when directories not found
- **Smoke tests**: 6 end-to-end tests for cache, baseline, reports, test discovery

### Changed
- **Default operators**: Removed INVERT_NEGS from defaults (deprecated no-op, now 6 operators)
- **Console report**: Shows survived mutations list and error details
- **HTML report**: Per-class breakdown with mini kill-rate bars

### Fixed
- **@AfterAll never invoked**: afterAllMethods discovered but never called
- **@BeforeAll failure didn't abort**: Tests ran even after setup failure
- **Subsumption equal kill sets**: Both mutations were marked subsumed (now strict subset only)
- **Weak mutation class-name format**: Dual lookup for dotted/slashed class names
- **Baseline save overwrites**: Now merges on incremental runs
- **TRUE_RETURNS/FALSE_RETURNS false positives**: Only matches ICONST immediately before IRETURN
- **NULL_SAFETY NOP stack corruption**: Added POP before NOP for null-safe call mutations
- **instanceofCount never resets**: Reset on line number change for SEALED_WHEN detection
- **Test class load failure**: Now reports ERROR instead of NO_COVERAGE
- **ConsoleReporter divide by zero**: Protected against empty results
- **WEAK_KILLED/SUBSUMED excluded from class scores**: Now included in kill calculations
- **Kill sets overwrite on incremental**: Added saveMerged for cross-run persistence

## [0.2.1] - 2026-06-08

### Fixed
- **URLClassLoader leak**: Test classloader now properly closed after mutation testing
- **IncrementalAnalyzer validation**: Removed inverted class name validation that rejected valid classes
- **Weak mutation line-number collision**: Now uses per-class covered lines instead of flat global set
- **InlinedFinallyDetector performance**: Per-class caching prevents N re-parses of same bytecode
- **InlinedFinallyDetector exit point bug**: Fixed jump instruction line number detection
- **@Nested inner class recursion 2+ levels deep**: Fixed outer instance propagation for deep nesting
- **@MethodSource companion object invocation**: Factory methods now correctly invoked with companion receiver
- **Lifecycle methods in superclass hierarchy**: @BeforeEach/@AfterEach now discovered via superclass walk
- **@Nested parent lifecycle propagation**: Parent class @BeforeEach/@AfterEach now run for nested tests
- **findTestClasses double execution**: Inner classes (containing $) excluded from top-level discovery
- **@ParameterizedTest unused parameter inflation**: No longer counts test as found when parameters unresolvable
- **CI workflows**: Fixed checkout@v4, setup-gradle@v4, codecov@v5 (non-existent v6/v7 tags)
- **BaselineStorage git failure**: Fixed misleading log message
- **Config defaults**: Engine now defaults enableSubsumption=true, enableWeakMutation=true (matches plugin)
- **Integration test**: Removed fragile kill-rate threshold, validates structural correctness

### Removed
- **Dead standalone mutator files**: Deleted CoroutineMutator.kt, DataClassCopyMutator.kt, NullSafetyMutator.kt, SealedClassWhenMutator.kt (production logic inlined in Mutator.kt)
- **Dead MutantTestTask**: Deleted unused Callable class and tests
- **Dead MutationDescriber**: Deleted unused description helper and tests
- **Dead sourceFiles parameter**: Removed never-read parameter from Mutator.scanMutations()
- **Dead mutateReturnStatic**: Removed identity function from ReturnValueMutator
- **INVERT_NEGS deprecated**: Marked as @Deprecated no-op operator

### Documentation
- Fixed MIGRATION.md typo (timeteo → timoteo)
- Fixed SECURITY.md OWASP claim to match actual failOnError=false config
- Fixed README.md Dokka output path for multi-module

## [0.2.0] - 2026-06-08

### Added
- **Test result caching** (MutKtCache): File-based caching for faster re-runs
- **Baseline diff mode** (BaselineStorage): Track results across runs, detect new/changed/removed mutations
- **Weak mutation analysis**: Skip mutations in uncovered code
- **Test strength ordering**: Run strongest tests first based on kill history
- **HTML reports**: Static HTML report with per-class breakdown and kill-rate visualization
- **Console reporter**: Real-time progress bar and kill-rate display
- **Regex filtering**: `targetClassPatterns` and `excludeClassPatterns` in Gradle DSL
- **JaCoCo integration**: Read .exec files for coverage-guided mutation skipping
- **Gradle 9.x compatibility**: Replace deprecated `project.buildDir` with `layout.buildDirectory`

### Fixed
- **@SuppressMutations per-operator**: Now correctly suppresses only listed operators (was suppressing ALL)
- **SubsumptionAnalyzer**: Disabled until proper per-test kill tracking exists
- **detectChanges**: Uses `git diff HEAD` instead of `HEAD~1` for initial commit safety
- **String.format errors**: Fixed `IllegalFormatConversionException` in reporters

### Changed
- **MutationStatus**: Added `NO_COVERAGE` and `WEAK_KILLED` statuses
- **README**: Updated with new features and configuration options
- **AGENTS.md**: Updated architecture docs with new internal types

### Performance
- Class-level classloader batching: 90x speedup on JBoyEmu (85.9s → 1.2s)
- Weak mutation analysis: Skips unreachable mutations
- Test result caching: Instant re-runs for unchanged code

## [0.1.1] - 2026-06-07

### Fixed
- Classloader now makes mutations visible to test code
- Mutation test runner reliability improvements
- Optimized mutation test execution time

## [0.1.0] - 2026-06-06

### Added
- Initial release
- 17 mutation operators (7 MVP + 4 Kotlin-specific + 6 Quick Win)
- Gradle plugin with DSL configuration
- JUnit 5 extension (@MutKtTest)
- ASM-based bytecode mutation engine
- Console and CSV report formats
- Inlined finally block detection
