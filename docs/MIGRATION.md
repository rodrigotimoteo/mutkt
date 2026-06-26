# Migration Guide

This guide helps you migrate between versions of the Kotlin Mutation Testing library.

## From 0.2.0 to 0.3.0

### Breaking Changes

- **`INVERT_NEGS` removed** — This operator was deprecated in 0.2.1 as a no-op. It is no longer present in `MutationOperator` and will fail to parse in `reportFormats`/`enabledOperators` DSL. Remove any references.
- **`failOnScoreThreshold` deprecated** — Use `failOnMutationScoreThreshold` instead. The old name still works but logs a deprecation warning and will be removed in 0.4.0.
- **Test runner switched to JUnit Platform Launcher** — `ReflectionTestRunner` was rewritten on top of `LauncherFactory.create()` + `LauncherDiscoveryRequestBuilder` (698 → 215 lines). JUnit Vintage engine is now included, so JUnit 4 tests keep working. Custom JUnit setup (engines, listeners) should now be configured through the standard `Launcher` API rather than reflection.
- **`@SuppressMutations(operators = [...])`** — Operator-specific suppression now works as documented. Previously the presence of the annotation suppressed ALL operators regardless of the `operators` list. If you relied on the old (broken) behavior, replace the annotation with `@SuppressMutations` (no operators) to keep the same effect.

### Removed

- `INVERT_NEGS` from `MutationOperator` and the default operator set.
- Dead standalone mutator files (`CoroutineMutator.kt`, `DataClassCopyMutator.kt`, `NullSafetyMutator.kt`, `SealedClassWhenMutator.kt`) — logic inlined into `Mutator.kt` private visitors.

## From 0.1.0 to 0.2.0

### Gradle Plugin Configuration

**Before (0.1.x):**
```kotlin
plugins {
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
}
```

**After (0.2.x):**
```kotlin
plugins {
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.2.0"
}
```

### New Configuration Options

The 0.2.x release adds several new configuration options:

```kotlin
mutationTest {
    // New: Fail build if mutation score below threshold (0-100)
    failOnScoreThreshold.set(80)

    // New: Fail build if mutation score below threshold (0-100)
    failOnMutationScoreThreshold.set(90)

    // New: Limit mutations per class (0 = no limit)
    maxMutationsPerClass.set(100)

    // New: Enable incremental analysis (caches results)
    enableIncrementalAnalysis.set(true)

    // New: Timeout per mutant in milliseconds
    mutantTimeoutMs.set(10000)
}
```

### New Operators

Six new mutation operators are available:

```kotlin
mutationTest {
    enabledOperators.addAll(
        "VOID_METHOD_CALLS",        // Remove calls to void methods
        "INCREMENTS",               // Replace ++ with -- and vice versa
        "TRUE_RETURNS",             // Replace boolean returns with true
        "FALSE_RETURNS",            // Replace boolean returns with false
        "CONSTRUCTOR_CALLS",        // Remove constructor calls
        "NON_VOID_METHOD_CALLS"     // Remove non-void method calls
    )
}
```

### Suppression Support

**New: Annotation-based suppression**
```kotlin
import com.github.rodrigotimoteo.mutation.annotation.SuppressMutations

@SuppressMutations(reason = "Generated code")
class BuildConfig { }

@SuppressMutations(operators = ["ARITHMETIC"], reason = "Trivial getter")
fun getName(): String = name
```

**New: Comment-based line suppression**
```kotlin
fun calculate(): Int {
    return value + 1  // mutflow:ignore
}

fun process(): String {
    return data.trim()  // mutflow:ignore:ARITHMETIC
}
```

### Report Formats

Two new report formats are available:

```kotlin
mutationTest {
    reportFormats.set(setOf("json"))  // or "xml", "html", "console"
}
```

### Breaking Changes

None. All 0.1.x configuration options continue to work.

### Deprecated Features

None.

### Removed Features

None.

## From 0.0.x to 0.1.0

### Initial Setup

This is the initial release. No migration needed.

```kotlin
plugins {
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.1.0"
}
```

### Basic Configuration

```kotlin
mutationTest {
    enabledOperators.addAll(
        "CONDITIONALS_BOUNDARY",
        "NEGATE_CONDITIONALS",
        "ARITHMETIC",
        "RETURN_VALS",
        "NULL_RETURNS",
        "EMPTY_RETURNS"
    )
    timeoutMs.set(30000)
    maxParallelMutants.set(4)
}
```

### Kotlin-Specific Operators

```kotlin
mutationTest {
    enabledOperators.addAll(
        "DATA_CLASS_COPY",      // Mutate data class copy()
        "SEALED_WHEN",          // Mutate sealed class when
        "NULL_SAFETY",          // Mutate ?., !!, ?:
        "COROUTINE"             // Mutate coroutines
    )
}
```

## General Migration Tips

### Testing Migration

1. **Update plugin version** in `build.gradle.kts`
2. **Run `./gradlew mutationTest`** to verify migration
3. **Check reports** for any unexpected behavior
4. **Adjust configuration** if needed

### Troubleshooting

**Issue: Plugin not found**
```kotlin
// Ensure correct plugin ID
plugins {
    id("io.github.rodrigotimoteo.mutation-kotlin") version "0.3.0"
}
```

**Issue: Configuration not applied**
```kotlin
// Ensure configuration is in the correct module
// For multi-module projects, add to each module's build.gradle.kts
```

**Issue: Tests not running**
```kotlin
// Verify test directory exists
// Run `./gradlew test` first to compile tests
```
