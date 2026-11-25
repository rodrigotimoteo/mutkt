# Local consumer rehearsal, 0.4.0-rc.1

Local substitute for the public consumer verification (see `docs/rc-checklist.md`), run on 2026-11-25 from the working tree of the RC branch. Nothing was published remotely.

## Setup

- Candidate: `./gradlew publishToMavenLocal -Pmutkt.version=0.4.0-rc.1 -Dmaven.repo.local=<scratch>/m2`, signed with a throwaway GPG key (the unsigned run failed then; fixed since, see the checklist findings). `tools/release/verify-publication.sh` on that repository: `publication verified: 31 signed files, 7 POMs`.
- Consumers: two standalone Kotlin/JVM builds outside the repository, Gradle 8.10, JDK 21, Kotlin 2.1.10, Jupiter 5.10.2 with `junit-platform-launcher` on the test runtime classpath, an empty `GRADLE_USER_HOME` and no included build. `mavenLocal()` (pointed at the scratch repository by `systemProp.maven.repo.local`) plus the Plugin Portal in `pluginManagement`, `mavenLocal()` plus Central in `dependencyResolutionManagement`. Plugin applied as `id("io.github.rodrigotimoteo.mutkt") version "0.4.0-rc.1"`.
- `buildEnvironment` resolves the marker `io.github.rodrigotimoteo.mutkt:io.github.rodrigotimoteo.mutkt.gradle.plugin:0.4.0-rc.1` to `mutkt-gradle`, `mutkt-core`, `mutkt-annotations`, `mutkt-asm`, `mutkt-junit` and `mutkt-report` 0.4.0-rc.1 (ASM 9.8, Platform launcher 1.10.2).
- Command per run: `./gradlew test mutkt`; `test` passed in every run.

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

Score 75.0% (3/4), then 100.0% (4/4). Matches the prediction.

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

Score 50.0% (1/2), then 100.0% (2/2). Matches the prediction.

## Not covered here

The public stages: release run link, Central and Plugin Portal resolution with a fresh cache, and repository propagation. They need the owner's tag and approval (see the checklist).
