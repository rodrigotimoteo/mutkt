# Changelog

## [0.1.2] - 2026-06-08

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
