# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions CI/CD workflows
- Security scanning (CodeQL, secret scanning)
- SBOM generation support
- Contributing guide
- Code of conduct
- Issue templates
- PR template
- Security policy
- Architecture documentation
- Migration guide

### Changed
- Improved build configuration
- Enhanced error messages

### Fixed
- Build stability improvements

## [0.2.0] - 2024-XX-XX

### Added
- 5 new mutation operators:
  - `VOID_METHOD_CALLS`: Remove calls to void methods
  - `INCREMENTS`: Replace ++ with -- and vice versa
  - `TRUE_RETURNS`: Replace boolean returns with true
  - `FALSE_RETURNS`: Replace boolean returns with false
  - `CONSTRUCTOR_CALLS`: Remove constructor calls
  - `NON_VOID_METHOD_CALLS`: Remove non-void method calls
- `@SuppressMutations` annotation for class/function level suppression
- Comment-based line suppression (`// mutflow:ignore`)
- Incremental analysis with `MutationHistoryManager`
- XML and JSON report generators
- `MutationDescriber` for human-readable mutation names
- Gradle plugin DSL options:
  - `failOnScoreThreshold`
  - `failOnCoverageThreshold`
  - `maxMutationsPerClass`
  - `enableIncrementalAnalysis`
  - `mutantTimeoutMs`

### Changed
- Updated Gradle plugin to version 0.2.0
- Improved mutation scanning performance
- Enhanced report formatting

### Fixed
- Timeout detection for infinite loops
- Incremental analysis caching issues
- Memory optimization for large codebases

## [0.1.0] - 2024-XX-XX

### Added
- Initial release
- 7 MVP mutation operators:
  - `CONDITIONALS_BOUNDARY`
  - `NEGATE_CONDITIONALS`
  - `ARITHMETIC`
  - `RETURN_VALS`
  - `NULL_RETURNS`
  - `EMPTY_RETURNS`
  - `INVERT_NEGS`
- 4 Kotlin-specific operators:
  - `DATA_CLASS_COPY`
  - `SEALED_WHEN`
  - `NULL_SAFETY`
  - `COROUTINE`
- Gradle plugin with auto-discovery
- HTML report generation
- JUnit 5 integration
- Zero-config usage
- Parallel mutant execution
- Coverage-guided test selection

### Changed
- N/A (initial release)

### Fixed
- N/A (initial release)

[Unreleased]: https://github.com/rodrigotimoteo/mutationKotlin/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/rodrigotimoteo/mutationKotlin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/rodrigotimoteo/mutationKotlin/releases/tag/v0.1.0
