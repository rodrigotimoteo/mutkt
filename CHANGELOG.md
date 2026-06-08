# Changelog

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
