# 0006 Android design

Status: accepted (2026-11-26).

## Context

P5 adds Android unit-test support (ROD-125, ROD-126..129). The owner fixed the shape: same plugin id, activated by the Android plugins, one variant, AGP's public `androidComponents` API through a `compileOnly` dependency, no reflection. API facts below were checked in AGP's `gradle-api` and `gradle` artifacts from maven.google.com (8.1.4, 8.4.2, 8.13.2, 9.0.1, 9.5.3; 9.5.3 is the current stable, 9.6 is in alpha).

## Decision

- **Activation.** `io.github.rodrigotimoteo.mutkt` reacts to `com.android.application` and `com.android.library` through `pluginManager.withPlugin`. The task stays `mutkt`; the JVM wiring is unchanged.
- **One variant.** DSL `mutkt { variant = "debug" }` (convention `debug`). The `onVariants` callback records each variant; the name check runs in an `afterEvaluate`, after the build script ran. If no variant has that name, or it has no unit-test component (`(variant as? HasUnitTest)?.unitTest == null`), the build fails with one message that lists the variant names.
- **Supported range: AGP 8.4.0 through the latest 9.x**, on Gradle 8.x and 9.x and JDK 17+. AGP 8.4 needs Gradle 8.6+.
- **Gradle 9 first.** AGP 9 needs Gradle 9.1+. Gradle 9 support for the JVM plugin is a separate 0.5.0 task, done before the Android work, and that task amends [0005](0005-support-matrix.md).
- **Dependency.** `compileOnly` `com.android.tools.build:gradle-api:8.4.2` (the minimum, catalog entry `agp-api`) in `mutkt-gradle` only; the build adds the `google()` repository. We never compile against a newer AGP than the floor.
- **Classes to mutate:** `variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(mutkt).toGet(ScopedArtifact.CLASSES, jars, dirs)`.
- **Test classes, test runtime classpath and test JVM settings:** read from the variant's AGP unit-test `Test` task, looked up lazily by AGP's task name `test<Variant>UnitTest`, the same way the JVM path reads `test`. If the task is missing, the build fails with one message that names the variant.
- **Compile classpath** (frame computation only): `variant.compileClasspath`. **Sources** (survivor snippets): `variant.sources.java?.all` and `variant.sources.kotlin?.all`.
- **Not used:** `sdkComponents.bootClasspath`, artifact-type strings, AAR extraction. The `Test` classpath already holds what AGP's own unit-test run uses (see below).
- **Out of scope:** instrumented (device) tests and the `src/androidTest` source set, test fixtures, screenshot tests, more than one variant per run, `com.android.dynamic-feature` and `com.android.test` modules.

## API facts (checked)

| AGP | Gradle floor (`VersionCheckPlugin.GRADLE_MIN_VERSION`) | `gradle-api` bytecode |
|---|---|---|
| 8.1.4 | 8.0 | Java 11 |
| 8.4.2 | 8.6 | Java 11 |
| 9.0.1 | 9.1.0 | Java 17 |
| 9.5.3 | 9.6.0 | Java 17 |

Why 8.4 and not lower:

- `HasUnitTest` (the non-deprecated way to reach `unitTest` on both 8.x and 9.x) appears in 8.1. `Variant.unitTest` is deprecated in 8.1 and `DeprecationLevel.HIDDEN` in 9.x ("removed in v10.0"), so it is not used.
- Gradle runs on JDK 21 from 8.5. AGP 8.4's floor (Gradle 8.6) is the first that lets the minimum CI cell run AGP on its own minimum Gradle on both our JDKs. AGP 8.1 (floor Gradle 8.0) cannot.

Classes:

- `ScopedArtifact.CLASSES` and `ScopedArtifactsOperation.toGet(type, inputJars, inputDirectories)` exist from 7.4 to 9.5.3 with the same JVM signature (javap). `toGet` is task-based only: `ScopedArtifacts` exposes nothing but `use(taskProvider)`. It fills two `ListProperty` inputs (`RegularFile` jars, `Directory` dirs) on the task and adds the task dependencies on compilation.
- 9.5.3 KDoc: CLASSES "includes users' transformation, but does not include Jacoco instrumentation". Not verified: that Kotlin and Java output both land there (KGP on 8.x, built-in Kotlin on 9.x), and whether `AsmClassVisitorFactory` instrumentation is included. ROD-126's TestKit test mutates a Kotlin and a Java class and pins both.
- The result can contain jars; `DirectoryClassSource` reads directories only, so ROD-126 must read jars too.
- `ScopedArtifact` changed its supertype from `Single<RegularFile>` (8.x) to `Single<Directory>` (9.x). We only pass the `CLASSES` object, which is binary-compatible.

Unit-test task:

- AGP exposes the unit-test `Test` task only through `UnitTest.configureTestTask(action)` (8.4+, `@Incubating`; "the action will only run if the task is configured") and `HostTest.withTestTaskProvider` (9.5+, `@Incubating`). Neither gives a lazy handle on 8.4, so we look the task up by name.
- AGP names it `computeTaskName("test")` on the unit-test component, i.e. `testDebugUnitTest` (checked in `AndroidUnitTest$CreationAction` 8.4.2 and 9.5.3). A missing task fails with one message naming the variant.
- Its `classpath` already contains the mockable `android.jar` (`MockableJarTransform`), R classes, the classes jars of AAR and Android library dependencies (AGP's internal `android-classes-jar` artifact type, not public API), Java resources and, with `unitTests.isIncludeAndroidResources`, Robolectric's `test_config.properties`. The transform classes exist in the AGP jar; the classpath content itself is confirmed by the ROD-126/127 TestKit tests. Rebuilding this from `unitTest.runtimeConfiguration` would need non-public artifact-type strings and has no public route to the mockable jar.
- `testClassesDirs`, `allJvmArgs` (`unitTests.all { }` settings), `workingDir` and `javaLauncher` come from the same task, with the coverage-agent filter the JVM path uses.

Other:

- `Component.compileClasspath` is `@Incubating` from 8.1 to 9.5.3.
- `Sources.java`/`kotlin` (`SourceDirectories.Flat.all`) exist from 7.4.
- `AndroidComponentsExtension.onVariants(selector, callback)` is unchanged from 8.1 to 9.5.3.

## Wiring and configuration cache

- `withPlugin` gets `AndroidComponentsExtension` by type and calls `onVariants(selector().all())`. The callback compares `variant.name` with the DSL value, registers `mutkt` with `tasks.register` and calls `toGet` on it. Nothing is resolved at configuration time.
- The name check runs in an `afterEvaluate` registered inside `withPlugin`, so it runs after AGP's own variant creation.
- MutKt compiles against AGP but does not ship it, so its class loader must see AGP's: AGP is declared in the same `plugins {}` block as MutKt or in a parent project's (for example in the root with `apply false`). With AGP only in a child scope, applying an Android plugin fails with one message that says so.
- The JVM rule "needs java or kotlin-jvm" accepts Android projects, because AGP's `BasePlugin` applies `JavaBasePlugin` (checked in 9.5.3). Its message must name Android.
- `Test` values are read through `providers.provider { tasks.named(name, Test).get() }`, never `TaskProvider.map`, which would make `mutkt` run the unit tests.
- The task holds only file collections, providers and plain values. No `Project`, `Variant` or AGP object reaches a task action, so configuration-cache entries store resolved files like the JVM path. ROD-126 runs its TestKit test with `--configuration-cache` twice (store, then reuse).
- Class swapping: the variant's classes reach the unit-test classpath as AGP-chosen dirs or jars, not always the `toGet` entries. The staged production directory stays first on the child classpath, so a mutant shadows the original. ROD-126 tests a library variant, whose classes reach the test classpath as a jar; ROD-127 tests an app variant, whose classes reach it as a jar too (`runtime_app_classes_jar`), next to an Android library module and an AAR from google().

## Scope of P5

| Issue | In scope | Out |
|---|---|---|
| ROD-126 | Activation, `variant` DSL, classes via `toGet`, `Test` task inputs, sources, jar reading in the class source; TestKit on min and latest AGP mutating a library class killed by a unit test | Several variants, flavours matrix |
| ROD-127 | TestKit: app module depending on an Android library module and an AAR; extractor only if that test fails with the `Test` classpath | Own AAR extraction by default |
| ROD-128 | Default exclude globs (R, BuildConfig, Hilt/Dagger, ViewBinding/DataBinding, Compose singletons), applied only when an Android plugin is present, replaceable and extendable; one match and one non-match test per glob | Annotation-based detection |
| ROD-129 | `samples/android` included build applying the plugin from source; Robolectric test with killed and surviving mutants; CI job; docs "Android" section | Published-artifact sample |

Instrumented tests (`connectedAndroidTest`, devices, emulators) are out of scope for P5 and have no planned task.

## KMP android target (P7)

`com.android.kotlin.multiplatform.library` registers a `KotlinMultiplatformAndroidComponentsExtension`, which in 9.5.3 is an `AndroidComponentsExtension` whose variant type is a `LibraryVariant` with `HasUnitTest`/`HasHostTests`. The same `onVariants` → `toGet(CLASSES)` → unit-test `Test` wiring applies; P7 adds the plugin id to the activation list. Two differences to settle in ROD-136:

- The target has a single variant, so the `variant` DSL does not apply.
- The host-test task does not follow `test<Variant>UnitTest` (compilation `hostTest`, source set `androidHostTest`). P7 needs `HostTest.withTestTaskProvider` (9.5+) or its own name rule, and probably a later AGP floor for KMP only.

## Risks

- Incubating APIs: `compileClasspath`, `configureTestTask` and `withTestTaskProvider` may change. Only `compileClasspath` is called; the `latest` CI cell catches breakage.
- The task name `test<Variant>UnitTest` is a convention, not API.
- Gradle 9: the plugin's TestKit suite runs on Gradle 9 since ROD-210; the build itself still uses an 8.x wrapper.
- Android TestKit tests need an Android SDK (`ANDROID_HOME`, preinstalled on GitHub's Ubuntu runners) and network access to google(). They run in their own `agpTest` task in `mutkt-gradle`, like `matrixTest`, excluded from `build` and Kover.

## CI (AGP versions tested)

| Cell | AGP | Gradle | JDK |
|---|---|---|---|
| min | 8.4.2 | 8.6 | 17, 21 |
| latest 8.x | 8.13.2 | 8.14.3 | 17 |
| latest | 9.5.3 | 9.6.0 | 17, 21 |

A dedicated CI job (`android`) runs `agpTest` on these cells with the runner's Android SDK, passing `-Pmutkt.agp` and `-Pmutkt.agpGradle` (defaults: the `min` cell). ROD-129 runs the sample on `min` and `latest`. The `latest` row moves with each stable AGP; `min` moves only through a new ADR.

## Consequences

- One `compileOnly` dependency and the `google()` repository in `mutkt-gradle`; no new published module, no runtime dependency on AGP.
- Android runs use the same task, worker and reports as JVM runs; only the inputs differ.
- Raising the AGP floor or adding instrumented tests needs a new ADR.
