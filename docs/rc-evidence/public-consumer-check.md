# Public consumer check, 0.4.0-rc.1

Public counterpart of [local-consumer-rehearsal.md](local-consumer-rehearsal.md) (see `docs/rc-checklist.md`), run on 2026-11-26 against the artifacts published to Maven Central. 0.4.0-rc.1 is not on the Plugin Portal listing; the Portal's `m2` repository redirects to Central.

## Setup

- Candidate: 0.4.0-rc.1 on Central (`repo1.maven.org/.../io.github.rodrigotimoteo.mutkt.gradle.plugin/maven-metadata.xml` lists `0.4.0-rc.1` as latest and release).
- No local copy: `~/.m2/repository/io/github/rodrigotimoteo` did not exist, so there was nothing to move aside. Neither consumer declares `mavenLocal()` or an included build.
- Consumers: two fresh standalone Kotlin/JVM builds outside the repository (same sources as the rehearsal), Gradle 8.10 (wrapper), JDK 21 (Corretto 21.0.11), Kotlin 2.1.10, Jupiter 5.10.2 with `junit-platform-launcher` on the test runtime classpath, a new empty `GRADLE_USER_HOME` (only the Gradle 8.10 distribution seeded). Default repositories only: `gradlePluginPortal()` in `pluginManagement`, `mavenCentral()` in `dependencyResolutionManagement`. Plugin applied as `id("io.github.rodrigotimoteo.mutkt") version "0.4.0-rc.1"`.
- Resolution: `./gradlew --no-daemon buildEnvironment --refresh-dependencies --info` succeeded. The marker `io.github.rodrigotimoteo.mutkt:io.github.rodrigotimoteo.mutkt.gradle.plugin:0.4.0-rc.1` resolves to `mutkt-gradle`, `mutkt-core`, `mutkt-annotations`, `mutkt-asm`, `mutkt-junit` and `mutkt-report` 0.4.0-rc.1 (ASM 9.8, Platform launcher 1.10.2), the same graph as the rehearsal.
- Source: `--info` shows every MutKt POM, `.module` and jar fetched through the Plugin Portal repository, e.g. `Downloading https://plugins.gradle.org/m2/io/github/rodrigotimoteo/mutkt-gradle/0.4.0-rc.1/mutkt-gradle-0.4.0-rc.1.jar`. The Portal answers `303 See Other` with `location: https://repo.maven.apache.org/maven2/io/github/rodrigotimoteo/mutkt-gradle/0.4.0-rc.1/mutkt-gradle-0.4.0-rc.1.jar` (checked with `curl -I`), so the bytes come from Central. The log also shows the redirected probe for the POM-only marker: `Resource missing. [HTTP HEAD: https://repo.maven.apache.org/maven2/io/github/rodrigotimoteo/mutkt/io.github.rodrigotimoteo.mutkt.gradle.plugin/0.4.0-rc.1/io.github.rodrigotimoteo.mutkt.gradle.plugin-0.4.0-rc.1.jar]`, which is expected.
- Both consumers shared the new `GRADLE_USER_HOME`: the first (`a`) downloaded everything; the second (`b`) revalidated with `--refresh-dependencies` and reused the cached files.
- Command per run: `./gradlew --no-daemon test mutkt`; `test` passed in every run.

## Scenario A: default operators

```kotlin
fun total(price: Int, qty: Int): Int = price * qty
fun isFree(total: Int): Boolean = total <= 0
```

Weak test: `assertEquals(6, total(2, 3))`, `assertTrue(isFree(-5))`, `assertFalse(isFree(10))`. Predicted: 4 mutants, the `total` mutants killed, the `isFree` boundary mutant surviving (no test at 0).

| Mutant id | Weak tests | After `assertTrue(isFree(0))` |
|---|---|---|
| `arithmetic:demo.PricingKt#total(II)I:0` | Killed by `PricingTest.total` | Killed |
| `return-vals:demo.PricingKt#total(II)I:0` | Killed by `PricingTest.total` | Killed |
| `negate-conditionals:demo.PricingKt#isFree(I)Z:0` | Killed by `PricingTest.isFree` | Killed |
| `conditionals-boundary:demo.PricingKt#isFree(I)Z:0` (`ifgt -> ifge`, line 5) | **Survived** | **Killed** by `PricingTest.isFree` |

Score 75.0% (3/4), then 100.0% (4/4). Matches the prediction and the rehearsal.

## Scenario B: `data-class-copy` only

`mutkt { operators.add("data-class-copy") }`

```kotlin
data class Point(val x: Int, val y: Int)
fun moveRight(p: Point): Point = p.copy(x = p.x + 1)
fun resetY(p: Point): Point = p.copy(y = 0)
```

Weak test: `assertEquals(Point(2, 5), moveRight(Point(1, 5)))`, and `resetY(Point(1, 2))` with its result unused. Predicted: 2 mutants (one per function; the members kotlinc generates in `Point` are not mutated), `moveRight` killed, `resetY` surviving.

| Mutant id | Weak tests | After `assertEquals(Point(1, 0), resetY(Point(1, 2)))` |
|---|---|---|
| `data-class-copy:demo.PointsKt#moveRight(Ldemo/Point;)Ldemo/Point;:0` (line 5) | Killed by `PointsTest.moveRight` | Killed |
| `data-class-copy:demo.PointsKt#resetY(Ldemo/Point;)Ldemo/Point;:0` (line 7, `Point.copy$default`) | **Survived** | **Killed** by `PointsTest.resetY` |

Score 50.0% (1/2), then 100.0% (2/2). Matches the prediction and the rehearsal.

## Not covered here

The Plugin Portal listing (0.4.0-rc.1 is not shown there) and JDK 17.
