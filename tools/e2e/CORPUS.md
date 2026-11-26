# E2E corpus

Real repositories for the local end-to-end suite (ROD-221), pinned in [corpus.json](corpus.json). Nothing here runs in CI. Run it with `tools/e2e/run.sh` (see [README.md](README.md)).

| Name | Type | Module (variant) | Gradle | AGP | Kotlin | JDK | DSL | Tests |
|---|---|---|---|---|---|---|---|---|
| [nowinandroid](https://github.com/android/nowinandroid) | android-app | `:core:data` (demoDebug) | 9.7.1 | 9.3.2 | 2.3.0 | 21 | kts | vintage, coroutines |
| [architecture-samples](https://github.com/android/architecture-samples) | android-app | `:app` (debug) | 8.11.1 | 8.7.3 | 2.1.10 | 17 | kts | vintage, coroutines |
| [tusky](https://github.com/tuskyapp/Tusky) | android-app | `:app` (blueDebug) | 8.14 | 8.10.0 | 2.1.21 | 21 | groovy | vintage, robolectric, coroutines |
| [chucker](https://github.com/ChuckerTeam/chucker) | android-lib | `:library` (debug) | 9.9.1 | 9.5.1 | 2.4.21 | 21 | kts | jupiter, vintage, robolectric, mockk, coroutines |
| [pluto](https://github.com/androidPluto/pluto) | android-lib | `:pluto:lib` (debug) | 8.12.1 | 8.6.0 | 1.9.22 | 17 | kts | vintage, robolectric |
| [strikt](https://github.com/robfletcher/strikt) | jvm-lib | `:strikt-core` | 8.10 | - | 2.0.10 | 17 | kts | jupiter |
| [spring-petclinic-kotlin](https://github.com/spring-petclinic/spring-petclinic-kotlin) | jvm-app | `:` | 9.9.2 | - | 2.4.21 | 17 | kts | jupiter, mockk |
| [gradle-semver-plugin](https://github.com/FigureTechnologies/gradle-semver-plugin) | jvm-lib | `:` | 8.11.1 | - | 1.9.24 | 17 | kts | kotest |
| [http4k](https://github.com/http4k/http4k) | jvm-lib | `:http4k-security-digest` | 9.8.2 | - | 2.4.0 | 21 | kts | jupiter |
| [diffuse](https://github.com/JakeWharton/diffuse) | jvm-app | `:formats` | 9.9.1 | - | 2.4.21 | 17 | groovy | vintage |

`jdk` is the JDK that runs Gradle; tusky, chucker and http4k also pin a JDK 21 toolchain, gradle-semver-plugin and spring-petclinic-kotlin a JDK 17 one. pluto has a Groovy `settings.gradle` and Kotlin build scripts.

## Unit tests at the pinned commits

Unit tests confirmed passing at the pinned commits (2026-11-26) for all 10 repos. strikt needs the `gradleArgs` placeholders for its publishing script. http4k targets `:http4k-security-digest`, because two `SunHttpTest` cases in `:http4k-core` fail locally.

## Choices

- `targetClasses` is the module's main package, narrowed to `util` for tusky to keep a run within minutes. http4k targets `:http4k-security-digest`: `:http4k-core` has tests that fail on the local Sun HTTP server.
- strikt has `gradleArgs` (`-PossrhToken=unused -PossrhTokenPassword=unused`): its `buildSrc` publishing script casts both properties to `String` at configuration time. They are placeholders, not credentials.
- `vintage` in `tests` means JUnit 4 tests, which also need the vintage engine and the Platform launcher on the test runtime classpath; the Gradle 9 Jupiter repos need the launcher declared.
- Left out: Kotlin Multiplatform projects (timber, kotlinpoet, kotlin-faker; MutKt applies to the Kotlin JVM and Android plugins only), projects whose tests run on a JDK 8 or 11 toolchain (hoplite, mockito-kotlin, kotlin-jdsl; the child JVMs use the `test` task's launcher), and ktlint (Kotlin release candidate, JDK 25 toolchain).
- nowinandroid is pinned to its last commit with green CI unit tests; the newer ones fail upstream. tusky is archived, which keeps it stable.
