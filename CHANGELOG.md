# Changelog

All notable changes to this project are documented here. Format: Keep a Changelog; versions: SemVer.

## [Unreleased]

## [0.4.0] - 2026-11-26

MutKt 0.4.0 is a full rebuild: it mutates the compiled `main` bytecode of a Kotlin/JVM or Java project and runs your existing JUnit tests against each mutant, with no test changes. It needs JDK 17 or 21, Gradle 8.10 or a later 8.x, and tests on the JUnit Platform (Jupiter by default, JUnit 4 through the vintage engine). The plugin id is `io.github.rodrigotimoteo.mutkt`, the Maven group `io.github.rodrigotimoteo`, and this is the first version on the Gradle Plugin Portal.

```kotlin
plugins {
    id("io.github.rodrigotimoteo.mutkt") version "0.4.0"
}
```

Then run `./gradlew mutkt`.

### Highlights

- **Operators.** Six run by default: `negate-conditionals`, `arithmetic`, `conditionals-boundary`, `null-returns`, `return-vals`, `empty-returns`. Ten more are opt-in: `constructor-calls`, `increments`, `true-returns`, `false-returns`, `void-method-calls`, `non-void-method-calls`, and the Kotlin-specific `sealed-when`, `null-safety`, `data-class-copy` and `coroutine`. See [docs/operators.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/operators.md).
- **Kotlin-aware filters.** No mutants in compiler-generated code: inlined function bodies, user-defined inline functions, the hidden calls behind `when` on strings and enums, and generated data class members. A `finally` block that the compiler copies to every exit is one mutant, not one per copy.
- **Forked test JVMs.** Mutants run in a pool of child JVMs set up like the `test` task (JVM arguments, system properties, Java launcher, classpath). Mocking agents are forwarded; coverage agents (JaCoCo, Kover) are not.
- **Baseline run.** The tests run once on the original classes first. A red suite fails the task before any mutant is scored, so an unrelated failure never counts as a kill.
- **Per-mutant deadlines.** Each mutant gets 4 times the baseline time plus 4 s, capped by `timeoutMs` (60 s by default). A hung mutant is killed and reported as TIMEOUT. The run warns when the cap cuts the deadline and suggests a value.
- **Statuses and score.** KILLED, SURVIVED, TIMEOUT, NO_COVERAGE and ERROR. Score = (KILLED + TIMEOUT) / (all mutants − ERROR). A mutant that exits its test JVM after the tests started counts as KILLED.
- **Reports** in `build/reports/mutkt/`: a console summary with a table per class and each survivor's source line, `mutkt.txt`, `mutation-report.json` (Stryker schema 2) and `mutation-report.html` (one offline page).
- **DSL.** `mutkt { }` with `targetClasses`, `excludedClasses` and `excludedMethods` (globs), `operators`, `threads`, `timeoutMs`, `maxMutantsPerClass`, `failBelowScore`, `engines`, `includeTags`, `excludeTags` and `reports.console`. See [docs/dsl.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/dsl.md).
- **Suppression.** `@SuppressMutations(operators = [...], reason = "...")` from `mutkt-annotations` (`compileOnly` is enough) skips mutants in a class or function and the lambdas and classes inside it.
- **Gradle.** The `mutkt` task is up to date until its inputs change and supports the configuration cache. It is not part of `check`; opt in with `tasks.check { dependsOn("mutkt") }`.

### Breaking changes from 0.3.x

0.4.0 is a rebuild, not an upgrade. The full list is in [docs/migration.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/migration.md).

- Plugin id `io.github.rodrigotimoteo.mutation-kotlin` is now `io.github.rodrigotimoteo.mutkt`; the extension `mutationTest { }` is `mutkt { }` and the task `mutationTest` is `mutkt`. There is no alias for the old id.
- Artifacts are the `mutkt-*` modules (same group `io.github.rodrigotimoteo`), the package is `com.github.rodrigotimoteo.mutkt`, and reports moved to `build/reports/mutkt`.
- Renamed options: `enabledOperators` to `operators` (kebab-case ids), `maxParallelMutants` to `threads`, `maxMutationsPerClass` to `maxMutantsPerClass`, and `failOnSurvived` and the score-threshold options to `failBelowScore`.
- Class filters are globs on dotted names; the regex and package options are gone, and so are the default exclusions of generated code. `excludedMethods` is empty by default.
- Removed options include `testClasses`, `classpath`, `coverageExecFile`, `outputDir`, `reportFormats`, the test pattern options, the cache, subsumption and weak-mutation switches, and the Android options.
- `@MutKtTest`, the JUnit extension and the `mutation-test-runner` module are gone. `@SuppressMutations` moved to `com.github.rodrigotimoteo.mutkt.annotations` and uses the new operator ids.
- `timeoutMs` defaults to 60 s and caps a deadline derived from the baseline. Only `junit-jupiter` runs by default; add `junit-vintage` to `engines` for JUnit 4.
- The score is now decimal and leaves out ERROR mutants, so re-baseline `failBelowScore`. The CSV, XML and graph reports are gone.

### Known limitations

- Libraries that load project classes by name through their own class loader do not see them in the test JVMs. MockK's `mockkStatic("com.example.Prices")` fails with `ClassNotFoundException`; use `mockkStatic(Prices::class)`.
- Class files read as resources in the test JVMs come back as `mutkt-class:` URLs, so libraries that need `file:` or `jar:` URIs for them may fail.
- Another project's classes on the test classpath (its test fixtures, for instance) still call the unmutated main classes, so mutants reached only through them survive.
- A class whose stack frames need a type missing from the classpaths and the Gradle JDK is skipped with a warning. Run Gradle on the same JDK as the tests.
- Kotlin/JVM and Java only. Android support is planned for 0.5.0; Kotlin Multiplatform comes later.

## [0.4.0-rc.1] - 2026-11-26

The release candidate of 0.4.0 above, published to Maven Central only (not to the Gradle Plugin Portal).
