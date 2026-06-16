# Architecture

## Overview

The Kotlin Mutation Testing library is designed as a modular, extensible system for performing mutation testing on Kotlin/JVM projects. It follows a plugin-based architecture that allows for easy addition of new mutation operators and reporting formats.

## Module Structure

```
mutationKotlin/
├── mutation-core/                # Core mutation engine
├── mutation-test-runner/         # JUnit Platform integration
├── mutation-gradle-plugin/       # Gradle plugin
├── mutation-report/              # HTML/JSON/XML/CSV/console/graph reports
├── mutation-sample/              # JVM example project
├── mutation-sample-android/      # Android (Robolectric) example project
└── docs/                         # Documentation
```

### Module Dependencies

```
mutation-gradle-plugin
    ↓
mutation-test-runner
    ↓
mutation-core
```

## Core Components

### 1. Mutation Engine (`mutation-core`)

The core module provides the fundamental mutation testing infrastructure.

#### Key Classes

- **`Mutator`**: Scans bytecode for mutation points and applies mutations
- **`MutationEngine`**: Orchestrates the mutation testing process
- **`IncrementalAnalyzer`**: Manages incremental analysis via git diff
- **`BaselineStorage`**: Persists mutation results for baseline comparison
- **`MutationScannerVisitor`**: ASM visitor for scanning mutation points
- **`MutationApplierVisitor`**: ASM visitor for applying mutations

#### Mutation Operators

Operators are implemented as enum values in `MutationOperator`:

```kotlin
enum class MutationOperator(val operatorName: String, val description: String) {
    CONDITIONALS_BOUNDARY("CONDITIONALS_BOUNDARY", "..."),
    NEGATE_CONDITIONALS("NEGATE_CONDITIONALS", "..."),
    ARITHMETIC("ARITHMETIC", "..."),
    // ... 16 total operators (1 dead operator removed)
}
```

#### Data Flow

1. Load class files from build output
2. Scan for mutation points using ASM
3. Generate mutants by applying mutations
4. Run tests against each mutant
5. Collect results and generate reports
6. Cache results for incremental analysis

### 2. Test Runner (`mutation-test-runner`)

The test runner module provides JUnit Platform integration.

#### Key Classes

- **`MutationTestRunner`**: Main entry point for running mutation tests

#### Features

- JUnit 5 integration
- Reflection-based test execution
- Parallel mutant testing
- Timeout detection

### 3. Gradle Plugin (`mutation-gradle-plugin`)

The Gradle plugin provides seamless integration with Gradle builds.

#### Key Classes

- **`MutationPlugin`**: Main plugin class
- **`MutationTask`**: Gradle task for running mutation tests
- **`MutationPluginExtension`**: DSL configuration

#### Features

- Auto-discovery of source sets
- JaCoCo coverage integration
- HTML/XML/JSON report generation
- Configuration via DSL

## Design Patterns

### 1. Visitor Pattern

Used extensively with ASM for bytecode manipulation:

- `ClassVisitor` for class-level scanning
- `MethodVisitor` for method-level scanning
- `AnnotationVisitor` for annotation processing

### 2. Strategy Pattern

Used for different mutation operators:

- Each operator implements specific mutation logic
- Operators can be enabled/disabled via configuration
- New operators can be added without modifying core code

### 3. Factory Pattern

Used for creating reporters:

- Report generators are selected based on configuration

### 4. Observer Pattern

Used for progress reporting:

- Task execution events
- Mutation testing progress
- Report generation status

## Data Models

### Core Models

- **`MutationInfo`**: Information about a mutation point
- **`Mutation`**: Represents a specific mutation
- **`MutationResult`**: Result of testing a mutation
- **`MutationReport`**: Complete test report

### Configuration Models

- **`MutationPluginExtension`**: Gradle DSL configuration
- **`MutationHistory`**: Incremental analysis history

## Performance Considerations

### Parallel Execution

- Mutants are tested in parallel using `ExecutorService`
- Configurable parallelism via `maxParallelMutants`
- Thread-safe mutation scanning

### Memory Management

- Class bytes are loaded on-demand
- Mutants are generated and tested incrementally
- History is cached to disk, not memory

### Incremental Analysis

- SHA-256 hashing for change detection
- Class-level caching of mutation results
- Test-level caching of execution results

## Security Considerations

### Bytecode Safety

- Mutations are validated before application
- Invalid bytecode is detected and rejected
- Timeout detection prevents infinite loops

### Dependency Safety

- Dependencies are scanned for vulnerabilities
- SBOM generation for supply chain security
- Code signing for published artifacts

## Extensibility

### Adding New Operators

1. Add enum value to `MutationOperator`
2. Implement scanner logic in `Mutator`
3. Implement applier logic in `MutationApplierVisitor`
4. Add tests

### Adding New Reporters

1. Implement report generator interface
2. Add to report generation pipeline
3. Configure via DSL

### Adding New Test Frameworks

1. Implement test runner interface
2. Add to test runner factory
3. Configure via plugin

## Testing Strategy

### Unit Tests

- Test individual mutation operators
- Test bytecode scanning logic
- Test report generation

### Integration Tests

- Test end-to-end workflows
- Test Gradle plugin integration
- Test with different Kotlin/Gradle versions

### Performance Tests

- Measure execution time
- Test memory usage
- Verify parallel execution

## Future Considerations

### K2 Compiler Plugin

- Native Kotlin IR access
- More precise mutations
- Better performance

### IDE Integration

- IntelliJ plugin
- Real-time mutation testing
- Interactive mutation exploration

### Cloud Integration

- Distributed mutation testing
- Result aggregation
- Team collaboration features
