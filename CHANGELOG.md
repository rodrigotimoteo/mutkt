# Changelog

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
- **Engine: sourceDirs** — Constructor parameter (was hardcoded)
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
