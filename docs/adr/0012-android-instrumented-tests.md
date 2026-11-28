# 0012 Android instrumented tests

Status: accepted (2026-11-28, ROD-256, owner).

## Context

MutKt runs a variant's local unit tests on the JVM (ADR 0006). Many Android projects test most of their logic on a device or emulator instead: `src/androidTest`, run by `connectedAndroidTest`, often with the AndroidX Test Orchestrator. On such a project MutKt's score is low for a reason the tool cannot see: the tests that would kill a mutant never run.

Running them would mean building a mutant APK, installing it on a device and running the instrumented tests with the orchestrator, once per mutant. Gradle has no per-mutant APK build, so this is new machinery for the Android path. It does not reuse the forked JVM pool (ADR 0008).

## Decision

Instrumented tests are out of scope for 0.6.0. MutKt runs local unit tests only. The FAQ entry "can MutKt run instrumented tests?", the Android section of getting-started and ADR 0006 say so. No implementation in 0.6.0.

Revisit when all of these hold:

1. A project whose kills depend on `androidTest` is measured, not estimated: the instrumented tests run once per mutant on a device, on a repo from the benchmark.
2. A run mode exists that avoids one install per mutant, for example one APK that carries every mutant and selects one at run time. Its cost is measured on the same repo.
3. The owner accepts a device or emulator in CI, with the time and flakiness that come with it.

## Cost estimate

Estimate, not measured. Per mutant run, serial on one emulator, assumed:

- build the mutant APK by repackaging the variant's dex with one class changed: 5 to 20 s
- install the app and the test APK: 3 to 10 s
- start the orchestrator, which starts a process per test class: 2 to 5 s
- the tests themselves are not counted

That is 10 to 35 s per mutant run. The runs per repo are the mutants minus NO_COVERAGE in [benchmark.md](../benchmark.md), since a mutant that no test reaches is not run. The unit-test times are the benchmark wall times, on 11 cores; instrumented runs would be serial.

| Repo | Mutants run | Unit tests today | Instrumented, estimate |
|---|---|---|---|
| nowinandroid | 83 | 1m | 14 min to 48 min |
| architecture-samples | 150 | 50s | 25 min to 88 min |
| tusky | 245 | 6m41s | 41 min to 2 h 23 min |
| chucker | 505 | 8m47s | 1 h 24 min to 4 h 55 min |

The estimate leaves out what the score needs beyond the runs: per-test coverage on a device (ADR 0010 relies on JVM probes, which a device run would need another way to produce) and the baseline run on the original APK. It is a lower bound.

## Consequences

- Android stays unit tests only; ADR 0006 holds as written.
- A project whose tests are mostly on a device gets a low score. The docs say why before it runs MutKt.
- MutKt takes on no emulator in CI and no AndroidX orchestrator dependency.
- Reopening the question needs measured numbers from the repo in question.
