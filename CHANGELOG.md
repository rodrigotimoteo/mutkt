# Changelog

All notable changes to this project are documented here. Format: Keep a Changelog; versions: SemVer.

## [Unreleased]

### Changed

- Breaking: Gradle 8.10 is no longer supported; the minimum is Gradle 8.11. MutKt is now built with Kotlin 2.4, which compiles for Kotlin 2.0 at the oldest, and Gradle 8.11 is the first Gradle with Kotlin 2.0 ([ADR 0005](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/adr/0005-support-matrix.md)).
- A Jupiter project no longer has to declare `junit-platform-launcher` (Gradle 9 adds none): when the test runtime classpath has `junit-platform-engine` but no launcher, `mutkt` resolves the launcher at the engine's version from the project's repositories, so the mutants run on the project's JUnit Platform rather than MutKt's 1.10.2. Android variants get the same lookup. If it cannot be resolved (offline), the bundled Platform is used as before and the build logs a warning naming the dependency to add.
- Kills are confirmed: the first time a test class kills a mutant, it runs once more on the original classes, and when it fails there too its kills are ERROR (`flaky test <name>`) rather than KILLED, so a test that fails alone or every time no longer inflates the score. Each distinct killing test class costs one more test class run ([faq](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/faq.md#what-about-flaky-tests)).
- Fewer mutants in code kotlinc generates: the coroutine state machine (`COROUTINE_SUSPENDED` checks and returns, the `Unit` result of a Unit suspend function, the continuation prologue and re-entry), enum `values`/`valueOf`/`getEntries`, and SAM adapter classes. On the ten release-check projects this removes 437 of 4891 mutants, including 49 of the 77 timeouts ([operators](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/operators.md#generated-code-on-the-e2e-corpus)).
### Added

- Opt-in `when-switch` operator: a `when` over Int, Char or String constants sends one case to the else branch, or the else branch to the first case ([docs/operators.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/operators.md)).
- Opt-in operator `remove-negation`: removes a unary minus (`INEG`/`LNEG`/`FNEG`/`DNEG`), so `-x` becomes `x`, as PIT's negation mutator does.
- Opt-in operator `stdlib-swap`: swaps a Kotlin stdlib collection, sequence, array or string call for its opposite with the same signature (`any`/`none`, `first`/`last`, `firstOrNull`/`lastOrNull`, `min`/`max` and their `OrNull` and `With` forms, `take`/`drop`, `sorted`/`sortedDescending`, and `Sequence.filter`/`filterNot`). Lambda overloads are inline and have no site ([docs/operators.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/operators.md)).
- Reports tell assertion kills from exception kills: a killed mutant's `statusReason` ends in `(assertion)` when the killing test failed with an `AssertionError` (any subclass: JUnit, kotlin.test, AssertJ, Truth, Kotest, Mockito) and `(exception)` otherwise, and the console summary adds `Killed 120 (assertions 95, exceptions 25)` above the score line. The score is unchanged.
- Opt-in operator `extreme`: replaces a whole method body with a default return (nothing, false, 0, `""`, empty, `this` for a builder, else null), one mutant per method, as Descartes does. A survivor marks a pseudo-tested method: tests run it, but none depends on what it does.
- Classes compiled from sources under `build/generated` (KSP, kapt, AGP: Room's `_Impl`, Moshi adapters) are skipped by default, found by their `SourceFile` rather than by name; a class is still mutated when a file of that name exists anywhere in the main source directories. `excludedClasses` starts with `<generated>`. A JVM build that sets `excludedClasses` with `=` (or `set`) drops the default `<generated>` entry and so still mutates generated classes; use `excludedClasses.add(...)` to keep it.
### Fixed

- Docs: mutants reached only through another project's classes on the test classpath (its test fixtures, for instance) are killed, as they have been since 0.5.0 loads each run in one class loader. The known-gap note is gone, and a multi-module build now tests it.

## [0.5.0] - 2026-11-27

Android support, and mutants that run only the tests that reach them.

### Added

- Android support, for local unit tests on AGP 8.4 through 9.x (tested on 8.4.2, 8.13.2 and 9.5.3; AGP 9 needs Gradle 9.1 or later). Setup in [docs/getting-started.md](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/getting-started.md#android).
  - The plugin activates on `com.android.application` and `com.android.library`. `mutkt { variant = "debug" }` (the default) picks one variant; a variant that does not exist or has no unit tests fails the build with the names of those that have them. AGP must be declared in the same `plugins { }` block as MutKt or a parent project's.
  - `mutkt` mutates the variant's Kotlin and Java classes as AGP compiled them, with AGP 8's Kotlin Android plugin or AGP 9's built-in Kotlin, and runs the tests with the classpath and JVM settings of its `test<Variant>UnitTest` task, without running that task. The configuration cache is supported.
  - `excludedClasses` starts with globs for generated classes (R, BR, BuildConfig, Hilt, Dagger, view and data binding, Compose); `add` extends them, `=` replaces them.
  - Multi-module apps: each module has its own `mutkt` task and reports, and an app's tests may use Android library modules and AAR dependencies.
  - Robolectric tests are supported. Each mutant gets its own test JVM, limited to two cores.
  - Instrumented tests (`src/androidTest`) are not supported.
- Gradle 9 support: the plugin is tested on Gradle 8.10, 8.14.3 and 9.9.2. On Gradle 9, declare `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`, because Gradle no longer adds it.
- Per-test coverage: the baseline run records which test classes reach each mutant. A mutant then runs only those classes, in baseline order, and one that no test reaches is NO_COVERAGE without a run ([ADR 0010](https://github.com/rodrigotimoteo/mutkt/blob/master/docs/adr/0010-per-test-coverage.md)). This works with Robolectric too. On the ten real projects of the release check, runs got much shorter, with no killed mutant lost; most former survivors are now reported as NO_COVERAGE. Mutants that coverage cannot place (class initialisers, `lazy` bodies created there) still run the whole suite. The JSON report lists each mutant's covering test classes in `coveredBy`.
- JUnit 4 projects (`useJUnit()` with only `junit:junit`) work with `engines = setOf("junit-vintage")`: MutKt supplies the vintage engine when the project has none.
- A documentation site with guides and the API reference: <https://rodrigotimoteo.github.io/mutkt/>.

### Fixed

- Tests that load project classes by name (`Class.forName`, Spring classpath scanning) or keep them in statics no longer see another mutant's class: each mutant run uses a fresh class loader in the test JVM.
- Robolectric tests no longer see a stale class from an earlier mutant.
- Kotest projects: Kotest's classpath scanning is turned off in the test JVMs, so its own jars no longer break mutant runs.
- Libraries that read a class's source location (strikt's `filepeek`, for instance) find the project's real output directory.
- A test JVM that stalls or runs out of memory after earlier runs is replaced and the mutant is retried once on a fresh JVM. After three stalls in a row the task stops with the cause, instead of reporting every remaining mutant as TIMEOUT.
- Test JVMs exit when the Gradle process goes away, and processes started by tests are killed with them.
- Each test JVM has its own temp directory, deleted when it ends, so files that tests leak (Robolectric, MockK) no longer fill the disk.
- A failing baseline names its cause.

## [0.5.0-rc.1] - 2026-11-27

The release candidate of 0.5.0 above, published to Maven Central and the Gradle Plugin Portal for a check on a real Android project.

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
