# Android sample

An Android library with a Robolectric test, mutated by MutKt. `Greeter` reads string resources, so `GreeterTest` needs Robolectric; `DiscountTest` is a plain JUnit 4 test. Both run on the vintage engine.

## Run

The sample takes the plugin from a local Maven repository that the repository's own wrapper fills. Gradle 9.6, which AGP 9.5 needs, cannot build the repository from source with Kotlin 2.1, so there is no `includeBuild`. Needs JDK 17 and an Android SDK (`ANDROID_HOME`).

```bash
./gradlew publishAllPublicationsToCentralRepository -Pmutkt.version=0.0.0-sample -Pmutkt.publishUrl=file://$PWD/build/sample-repo
gradle -p samples/android mutkt                    # AGP 8.4.2, run with Gradle 8.6 or later
gradle -p samples/android mutkt -Pmutkt.agp=9.5.3  # AGP 9.5.3, run with Gradle 9.6.0 or later
```

`-Pmutkt.agp` overrides the catalog's `agp` version. On AGP 9 the build applies no Kotlin Android plugin, since AGP compiles Kotlin itself.

## Expected result

`build/reports/mutkt/mutkt.txt` reports 6 mutants: 5 killed, 1 survived. The survivor is the `conditionals-boundary` mutant of `Discount.price` (`>=` to `>`): no test uses a total of exactly 100. CI's `android-sample` job checks these counts on the min and latest AGP cells.

## Robolectric

The first test run downloads Robolectric's `android-all` jar for SDK 34 into `~/.m2/repository/org/robolectric`, outside Gradle's caches. CI caches that directory. With Robolectric on the test classpath, MutKt starts a fresh test JVM for each mutant (see the [FAQ](../../docs/faq.md)), so a run takes longer than on the JVM.
