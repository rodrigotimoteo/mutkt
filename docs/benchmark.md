# Benchmark

MutKt on 10 open-source repos from the end-to-end suite. Each repo is pinned to a commit and checked against its expectations. Numbers are from the release-gate run of v0.5.0 (commit `1a545ac`, 2026-11-27).

Score is (Killed + Timeout) / (mutants - ERROR), as in the console summary. NoCoverage mutants count against it.

| Repo | Kind | Test style | Mutants | Score | Killed / Survived / NoCoverage / Timeout | Wall time | Machine |
|---|---|---|---|---|---|---|---|
| nowinandroid | android-app | vintage, coroutines | 171 | 39.8% | 44 / 15 / 88 / 24 | 1m | MacBook, 11 cores |
| architecture-samples | android-app | vintage, coroutines | 1158 | 11.1% | 110 / 22 / 1008 / 18 | 50s | MacBook, 11 cores |
| tusky | android-app | vintage, robolectric, coroutines | 739 | 27.5% | 180 / 42 / 494 / 23 | 6m41s | MacBook, 11 cores |
| chucker | android-lib | jupiter, vintage, robolectric, mockk, coroutines | 1120 | 33.6% | 369 / 129 / 615 / 7 | 8m47s | MacBook, 11 cores |
| pluto | android-lib | vintage, robolectric | 415 | 2.4% | 10 / 2 / 403 / 0 | 54s | MacBook, 11 cores |
| strikt | jvm-lib | jupiter | 554 | 69.0% | 379 / 96 / 76 / 3 | 1m25s | MacBook, 11 cores |
| spring-petclinic-kotlin | jvm-app | jupiter, mockk | 64 | 81.2% | 50 / 7 / 5 / 2 | 4m07s | MacBook, 11 cores |
| gradle-semver-plugin | jvm-lib | kotest | 241 | 12.4% | 30 / 0 / 211 / 0 | 17s | MacBook, 11 cores |
| http4k | jvm-lib | jupiter | 80 | 78.8% | 63 / 6 / 11 / 0 | 1m3s | MacBook, 11 cores |
| diffuse | jvm-app | vintage | 349 | 24.9% | 87 / 12 / 250 / 0 | 11s | MacBook, 11 cores |
