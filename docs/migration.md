# Migrating from 0.3.x to 0.4.0

0.4.0 is a rebuild, not an upgrade: the plugin id, the extension, the task, the operator ids and several defaults changed. The 0.3.x line is tag `v0.3.3`.

## Setup

| 0.3.x | 0.4.0 |
|---|---|
| plugin id `io.github.rodrigotimoteo.mutation-kotlin` | `io.github.rodrigotimoteo.mutkt` |
| extension `mutationTest { }` | `mutkt { }` |
| task `mutationTest` | `mutkt` |
| reports in `build/reports/mutation` | `build/reports/mutkt` |
| artifacts `mutation-core`, `mutation-test-runner`, ... | `mutkt-*` modules, same group `io.github.rodrigotimoteo` |
| package `com.github.rodrigotimoteo.mutation` | `com.github.rodrigotimoteo.mutkt` |

Recommended (owner to confirm): no alias for the old plugin id. A build that still applies it fails with "plugin not found" instead of loading a plugin whose DSL differs in almost every property below.

## DSL properties

"Removed" means 0.4.0 has no equivalent. Unless a row says otherwise the feature is not planned for 0.4.0. An unknown property fails the build script, so nothing is silently ignored.

| Old property | New | Notes |
|---|---|---|
| `targetClasses` | removed | Was a class directory collection. The `main` output is used; the name now means class globs, see Behavior changes |
| `testClasses` | removed | The `test` task's classes and classpath are used |
| `classpath` | removed | The `test` task's runtime classpath is used |
| `coverageExecFile` | removed | No JaCoCo input; the baseline run replaces it (behavior changes) |
| `autoRunJaCoCo` | removed | Was already a no-op |
| `enabledOperators` | `operators` | Ids are kebab-case (`arithmetic`), see [operators.md](operators.md); empty runs the default six |
| `timeoutMs` | `timeoutMs` | Default 30000 ms became 60000 ms, and it now caps a deadline derived from the baseline run (see `docs/dsl.md`) |
| `mutantTimeoutMs` | `timeoutMs` | Deprecated duplicate |
| `maxParallelMutants` | `threads` | Default 4 became the available processors |
| `outputDir` | removed | Fixed at `build/reports/mutkt` |
| `failOnSurvived` | `failBelowScore` | `failBelowScore = 100` is stricter: it also fails on NO_COVERAGE. ERROR mutants are excluded and TIMEOUT counts as detected |
| `failOnMutationScoreThreshold` | `failBelowScore` | 0 meant off; unset means off |
| `failOnScoreThreshold` | `failBelowScore` | Deprecated alias |
| `maxMutationsPerClass` | `maxMutantsPerClass` | Renamed; 0 meant unlimited, unset means unlimited |
| `excludedClasses` | `excludedClasses` | Globs on dotted class names, default empty, see Behavior changes |
| `excludedMethods` | `excludedMethods` | `method` or `pkg.Class#method`; default empty (it was `main`, `toString`, `hashCode`, `equals`, `clone`, `finalize`) |
| `excludeGeneratedClasses` | `excludedClasses` | Android projects start with generated-class globs again (R, BuildConfig, Hilt, Dagger, bindings, Compose; see `docs/dsl.md`); other projects have none, list what you need |
| `targetClassPatterns` | `targetClasses` | Regex became glob |
| `excludeClassPatterns` | `excludedClasses` | Regex became glob |
| `targetPackages` | `targetClasses` | Use `com.example.service.**` |
| `excludePackages` | `excludedClasses` | Use `com.example.generated.**` |
| `targetTestPatterns` | removed | All `test` classes run; narrow with `includeTags` |
| `excludeTestPatterns` | removed | Narrow with `excludeTags` |
| `reportFormats` | removed | Always written: console summary, `mutkt.txt`, `mutation-report.json` (Stryker schema) and `mutation-report.html` in `build/reports/mutkt/` (ADR 0003) |
| `showClassScores` | removed | Always shown: the console summary has a table per class, the HTML report scores each file |
| `generateGraph` | removed | The graph report is not planned for 0.4.0 |
| `ciMode` | removed | `reports.console` and `failBelowScore` cover it |
| `verbose` | removed | Was already a no-op |
| `enableIncrementalAnalysis` | removed | The task is up to date until its inputs change; no git-diff mode |
| `enableCache` | removed | No result cache |
| `enableSubsumption` | removed | No subsumption pass |
| `enableWeakMutation` | removed | No weak-mutation pass |
| `enableInlinedFinally` | removed | No inlined-finally filter |
| `enableTestOrdering` | removed | No test ordering |
| `isAndroid` | removed | From 0.5.0, MutKt activates when `com.android.application` or `com.android.library` is applied (AGP 8.4 to 9.x, see [getting-started.md](getting-started.md#android)) |
| `androidPluginType` | removed | Read from the applied Android plugin |
| `androidVariant` | `variant` (0.5.0) | Default `debug`; one variant per run |
| `androidContext` | removed | MutKt reads the variant's classes and unit-test task from AGP |

New in 0.4.0 with no old counterpart: `engines`, `includeTags`, `excludeTags` (they were constructor arguments of the old engine, not DSL), and `reports.console`. See [dsl.md](dsl.md).

## Removed APIs

ADR 0004: no JUnit extension API and no runner module. Existing tests run unchanged against each mutant.

- `@MutKtTest`, the JUnit `MutKtExtension` and `MutKt.underTest { }`: delete them from tests; the `mutation-test-runner` dependency goes with them.
- `MutationRegistry` (a JVM-wide singleton) and the programmatic `MutationEngine` / `MutationEngineConfig` entry points: not published.
- `@SuppressMutations` stays, with the same `operators` and `reason`, but moved to `com.github.rodrigotimoteo.mutkt.annotations` (module `mutkt-annotations`). Operator names in `operators = [...]` use the new ids.

## Behavior changes

- **Score.** 0.3.x: killed / total as a rounded integer. 0.4.0: (KILLED + TIMEOUT) / (total - ERROR) as a decimal; NO_COVERAGE and SURVIVED count as not detected, ERROR mutants are left out (ADR 0002). Scores move, so re-baseline `failBelowScore`.
- **Class filters.** Globs only, on dotted names: `*` stays in a segment, `**` crosses dots. 0.3.x slashed patterns such as `**/*Test` become `**Test`. JVM projects have no default exclusions any more (0.3.x skipped tests, `BuildConfig`, `R`, Dagger and Hilt output and more): only `main` classes are mutated, but exclude generated code yourself. Android projects start `excludedClasses` with globs for R, BR, BuildConfig, Hilt, Dagger, view and data binding and Compose (see [dsl.md](dsl.md#android-generated-classes)); `add` extends them and `=` replaces them.
- **Operators.** The default set is the same six as 0.3.x (`arithmetic`, `conditionals-boundary`, `negate-conditionals`, `null-returns`, `return-vals`, `empty-returns`). A non-empty `operators` runs exactly those ids. Site differences per operator are in [parity.md](parity.md).
- **JUnit engines.** Default `junit-jupiter` only; 0.3.x also ran `junit-vintage` and `junit-platform-suite-engine`. Opt in with `engines` and put the engine on the test runtime classpath (ADR 0005); MutKt supplies the vintage engine itself when the project has none.
- **Forked JVM pool.** Mutants run in child JVMs that copy the `test` task's JVM setup; a hung mutant is killed and reported as TIMEOUT (ADR 0008).
- **Java agents.** As in 0.3.x, the `test` task's `-javaagent` arguments reach the test runs, so mocking agents (MockK, Mockito inline) keep working; coverage agents (JaCoCo, Kover) are now dropped. MockK's `mockkStatic("name")` string form cannot find project classes; use `mockkStatic(Type::class)`. See the [FAQ](faq.md#do-mocking-libraries-work).
- **Baseline run.** The selected tests run once on the original classes first; a red baseline fails the task before any mutant is scored.
- **Reports.** Console summary, `mutkt.txt`, `mutation-report.html` and Stryker-schema `mutation-report.json` in `build/reports/mutkt/` (ADR 0003); the CSV, XML and graph formats are gone.
- **Gradle.** The task is up to date when nothing changed and supports the configuration cache. Requires JDK 17 or 21 and Gradle 8.10 or later, including 9.x.
