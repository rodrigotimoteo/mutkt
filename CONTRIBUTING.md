# Contributing to Kotlin Mutation Testing

Thank you for your interest in contributing! This document provides guidelines and information for contributors.

## Development Setup

### Prerequisites

- JDK 17 or later
- Gradle 8.10 or later
- Git

### Getting Started

1. **Fork the repository**
   ```bash
   git clone https://github.com/your-username/mutationKotlin.git
   cd mutationKotlin
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run tests**
   ```bash
   ./gradlew test
   ```

4. **Run mutation tests**
   ```bash
   ./gradlew mutationTest
   ```

## Project Structure

```
mutationKotlin/
├── mutation-core/           # Core mutation engine
├── mutation-test-runner/    # JUnit Platform integration
├── mutation-gradle-plugin/  # Gradle plugin
├── mutation-sample/         # Example project
└── docs/                    # Documentation
```

## Code Style

### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc for public APIs
- Keep functions small and focused

### Git Commit Messages

- Use conventional commits format
- Examples:
  - `feat: add new mutation operator`
  - `fix: resolve timeout detection issue`
  - `docs: update API documentation`
  - `test: add unit tests for new feature`

## Pull Request Process

1. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write clean, well-documented code
   - Add tests for new features
   - Update documentation if needed

3. **Run tests**
   ```bash
   ./gradlew test
   ./gradlew build
   ```

4. **Commit your changes**
   ```bash
   git commit -m "feat: add your feature description"
   ```

5. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request**
   - Provide a clear description
   - Reference any related issues
   - Ensure CI passes

## Testing

### Unit Tests

- Write unit tests for new features
- Aim for high test coverage
- Use descriptive test names

### Integration Tests

- Test end-to-end workflows
- Verify Gradle plugin integration
- Test with different Kotlin/Gradle versions

### Performance Tests

- Measure execution time
- Test memory usage
- Verify parallel execution

## Documentation

### Code Documentation

- Add KDoc for all public APIs
- Include usage examples
- Document parameters and return values

### User Documentation

- Update README for new features
- Add migration guides for breaking changes
- Update changelog

## Issues

### Bug Reports

When reporting bugs, please include:

- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment details (OS, Java version, Gradle version, etc.)

### Feature Requests

When requesting features, please include:

- Use case description
- Expected behavior
- Any alternatives considered

## Code of Conduct

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Questions?

If you have questions, feel free to:

- Open an issue
- Start a discussion
- Contact the maintainers

Thank you for contributing!
