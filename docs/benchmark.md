# Benchmark

MutKt on 10 open-source repos from the end-to-end suite. Each repo is pinned to a commit and checked against its expectations. Numbers are from the release-gate run of v0.6.0-rc.1 (commit `5722635`, 2026-11-28).

Score is (Killed + Timeout) / (mutants - ERROR), as in the console summary. NoCoverage mutants count against it.

| Repo | Kind | Test style | Mutants | Score | Killed / Survived / NoCoverage / Timeout | Wall time | Machine |
|---|---|---|---|---|---|---|---|
| nowinandroid | android-app | vintage, coroutines | 50 | 50.0% | 25 / 10 / 15 / 0 | 42s | MacBook, 11 cores |
| architecture-samples | android-app | vintage, coroutines | 161 | 52.2% | 84 / 4 / 73 / 0 | 38s | MacBook, 11 cores |
| tusky | android-app | vintage, robolectric, coroutines | 757 | 28.0% | 185 / 41 / 504 / 27 | 5m56s | MacBook, 11 cores |
| chucker | android-lib | jupiter, vintage, robolectric, mockk, coroutines | 812 | 38.8% | 312 / 50 / 447 / 3 | 5m1s | MacBook, 11 cores |
| pluto | android-lib | vintage, robolectric | 352 | 1.1% | 4 / 0 / 348 / 0 | 28s | MacBook, 11 cores |
| strikt | jvm-lib | jupiter | 425 | 82.6% | 351 / 15 / 59 / 0 | 39s | MacBook, 11 cores |
| spring-petclinic-kotlin | jvm-app | jupiter, mockk | 61 | 77.0% | 45 / 9 / 5 / 2 | 1m31s | MacBook, 11 cores |
| gradle-semver-plugin | jvm-lib | kotest | 182 | 16.5% | 30 / 0 / 152 / 0 | 15s | MacBook, 11 cores |
| http4k | jvm-lib | jupiter | 71 | 84.5% | 60 / 7 / 4 / 0 | 53s | MacBook, 11 cores |
| diffuse | jvm-app | vintage | 288 | 24.0% | 69 / 13 / 206 / 0 | 10s | MacBook, 11 cores |
