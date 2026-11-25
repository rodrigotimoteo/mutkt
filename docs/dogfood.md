# Dogfood

MutKt mutation-tests its own code with the plugin built from this repository.

```bash
./gradlew -p tools/dogfood mutkt --continue
```

`tools/dogfood` is a standalone build, outside `./gradlew build`. Its projects apply the repository's `mutkt.kotlin-library` convention from `build-logic`. It includes the root build twice: in `pluginManagement`, so `id("io.github.rodrigotimoteo.mutkt")` resolves to the plugin compiled from the working tree, and as a regular included build, so `io.github.rodrigotimoteo:mutkt-*` dependencies are the repository's modules. A build cannot apply a plugin it compiles itself, hence the separate build. Each project compiles the sources and tests of one module (`core` from `mutkt-core`, `asm` from `mutkt-asm`) and runs `mutkt` on them. `ArchitectureTest`, `ParityDocTest` and `OperatorDocTest` are left out: they read the repository root and its docs, not the code under mutation. The `asm` dependencies are listed by hand: the `mutkt-asm` artifact would bring them, but also a second copy of the classes under mutation.

The fork child has the plugin's own `mutkt-core` jar on its JVM classpath (for the domain types of the fork protocol), but the project's classes load through a loader whose parent is the platform loader and that shares only the JUnit Platform packages with the plugin, so the tests see the mutated class, not the plugin's copy. The kills in every `mutkt-core` class, `domain.Mutant` included, confirm it: with the plugin's copy visible, no core mutant could be killed.

The CI job `dogfood` (pull requests and `rebuild/**` branches) runs it and writes each score to the job summary. Reports: `tools/dogfood/<core|asm>/build/reports/mutkt/mutkt.txt`.

| Module | Scope | Settings | Measured | `failBelowScore` |
|---|---|---|---|---|
| `mutkt-core` | `com.github.rodrigotimoteo.mutkt.**` | defaults | 82.5% (97 mutants, about 20 s) | 75 |
| `mutkt-asm` | `com.github.rodrigotimoteo.mutkt.asm.operators.**` | `timeoutMs = 120000`, `maxMutantsPerClass = 3` | 93.7% (63 evaluated, 185 omitted by the cap; about 5 min) | 90 |

Measured on 2026-11-25, JDK 21, 8 threads, offline. Members kotlinc generates for data classes are not mutated (see Noise filters in `docs/operators.md`); before that, `mutkt-core` measured 44.5% over 263 mutants, most survivors in those members.

`mutkt-asm` runs its whole test suite per mutant and the suite compiles Kotlin fixtures, so a run takes seconds: with the default 10 s deadline almost every mutant ends in TIMEOUT. The baseline run took about 36 s alone and a mutant run up to 2.9 times that with 8 threads (2026-11-25, 11 cores), which sets the factor of the per-mutant deadline (see `timeoutMs` in `docs/dsl.md`); here `timeoutMs` caps it. The cap keeps the job within minutes; without it the 248 mutants take about 18 minutes locally.

The `mutkt-asm` gate is weak: the cap keeps the first 3 mutants of each class in discovery order, not a sample, and TIMEOUT counts as detected, so a slow or contended runner can raise the score. Read it as a smoke check of the operators, not as their mutation score.

## Ratchet

- The threshold is the measured score rounded down, minus a margin of about 4 points, so scheduling noise (TIMEOUT counts as detected) does not fail the job.
- When a change raises a measured score by 5 points or more, raise `failBelowScore` in `tools/dogfood/<module>/build.gradle.kts` in the same change and update this table. Never lower it to make a change pass: fix the tests, or record why in the PR.
- Raising `maxMutantsPerClass` (or dropping it) is a ratchet step too: remeasure and reset the threshold in the same change.
