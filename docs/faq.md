# FAQ

## What counts as killed?

A mutant is KILLED when a test or a container (a test class, its `@BeforeAll`) fails while the mutant is loaded: a failed assertion and an exception thrown by the mutated code both count. The reports tell them apart: the console summary adds a line above the score, `Killed 13 (assertions 11, exceptions 2)`, that splits the kills into assertions (an `AssertionError` or a subclass, which JUnit, kotlin.test, AssertJ, Truth, Kotest and Mockito throw) and exceptions (any other throwable, a child JVM that died included), and the JSON `statusReason` ends in `(assertion)` or `(exception)`. A kill by an exception is often weaker: a null return that fails three calls later says little about what the test checks. Aborted tests (failed assumptions) and disabled tests do not. The test classes run one at a time, sorted by name, and the run stops at the first class with a failure; the report names that first failing test (`killedBy` in the JSON report).

A mutant that exits its child JVM once the tests have started is KILLED too, as in PIT: a `System.exit` or an `OutOfMemoryError` (JUnit does not catch it). `killedBy` then reads `JVM exit 7` (the exit code) or `OutOfMemoryError`. A child that served earlier mutants may run out of memory or time from what they left behind, so an `OutOfMemoryError` or TIMEOUT there is run once more on a fresh child, and that result counts; a real TIMEOUT there takes up to two deadlines. When the test JVMs cannot start, or cannot load the classes and discover the tests within 60 s, for 3 mutants in a row, the task fails with that message and writes no report. A `StackOverflowError` in a test is an ordinary test failure. These stay ERROR: a child ended by a signal (exit code above 128, such as the OS out-of-memory killer's 137), reported as `killed by signal 9`, since that is usually the machine rather than the mutant; and a child that dies earlier, while it loads the classes and discovers the tests. If the baseline run exits the JVM, the task fails with `the test JVM exited with code 7 on the original classes`.

The other statuses:

| Status | Meaning | Score |
|---|---|---|
| SURVIVED | Every test that ran passed | not detected |
| TIMEOUT | The tests did not finish within the mutant's deadline | detected |
| NO_COVERAGE | No test reaches the mutated code, so none ran against it; also when `includeTags` or `engines` select no test | not detected |
| ERROR | The mutant could not be loaded or run (a `VerifyError`, a child JVM that died before the tests started) | left out |

Score = (KILLED + TIMEOUT) / (all mutants − ERROR).

## Which tests run against a mutant?

The baseline run (below) records which test classes reach each mutated instruction. A mutant then runs only those classes, in the order the baseline ran them, and one that no test class reaches is NO_COVERAGE without a run. Coverage says a test executed the code, not that it checked the result: a test that reaches a mutant but never checks its effect still yields SURVIVED.

Some mutants run every selected test instead: one whose code was reached outside any test class (while JUnit discovered the tests, for instance), one in a class initialiser or in a `lazy { }` of an object, a companion object or a top-level property (they run once, under whichever test class gets there first), and one in a class that cannot be instrumented (an interface, a method near the JVM size limit). If the tests fail on the instrumented classes but pass on the original ones, the run warns `The baseline on probed classes failed (<cause>); running every mutant against the whole suite` and goes on that way.

## Why does TIMEOUT count as detected?

A mutant that makes a test run past the deadline usually turned a loop or a recursion endless: the tests noticed a change, they just never finished. The deadline is per mutant and starts when the tests start, after class loading. It is 4 times the sum of how long the mutant's test classes ran on the original classes (the baseline run) and the time of the baseline's longest test class, plus 4 s, at most `timeoutMs` (60 s by default). The longest class is an allowance for class loading and shared set-up, such as a Spring context, that the baseline paid once and every mutant's run pays again. A mutant that runs the whole suite gets 4 times the whole suite's time plus 4 s. A child JVM that passes it is killed and replaced, so an endless loop does not hold a thread.

If many mutants end in TIMEOUT, the suite is too slow for the deadline. When `timeoutMs` cut the deadline, the run warns with `Per-mutant deadline capped at timeoutMs` and names the value to set. Raise `timeoutMs`, or lower `threads`: deadlines scale from a baseline run alone, so `threads` above the core count can cause false TIMEOUTs. Otherwise a slow or busy machine can inflate the score. The baseline run gets 5 times `timeoutMs`, since its JVM starts cold.

## What about flaky tests?

Before any mutant, MutKt runs the selected tests once on the original classes. If anything fails there, the task fails with `The tests fail on the original classes, so no mutant was scored` and writes no report (the reports of an earlier run stay in place), so a red suite can never count as kills.

That baseline runs once, so it does not catch flakiness. A flaky test that fails during a mutant's run kills that mutant and inflates the score. Tag such tests and leave them out:

```kotlin
mutkt { excludeTags.add("flaky") }
```

## Equivalent mutants

Some mutants cannot change behavior (`x * 1` to `x / 1`), so no test can kill them. MutKt does not detect them; [operators.md](operators.md) lists the known cases per operator. To skip code where they appear, annotate it:

```kotlin
@SuppressMutations(operators = ["arithmetic"], reason = "multiplying by 1 keeps the call sites uniform")
fun scale(x: Int): Int = x * 1
```

The annotation is `com.github.rodrigotimoteo.mutkt.annotations.SuppressMutations` from `io.github.rodrigotimoteo:mutkt-annotations` (`compileOnly` is enough: MutKt reads it from the bytecode). It applies to a class or a function, and to the lambdas and classes declared inside it. Without `operators` it suppresses every operator. An unknown operator id fails the run when the annotation is on a targeted class or on code enclosing one; elsewhere it is ignored. `excludedClasses` and `excludedMethods` in the [DSL](dsl.md) skip code by name instead.

## Which `-javaagent` arguments are forwarded?

The child JVMs copy the `test` task's JVM arguments, `-javaagent` included, except coverage agents: an agent whose jar name contains `jacoco`, `kover` or `intellij-coverage` is dropped, so coverage never records mutant runs or rewrites its data files. Other agents, such as a mocking library's, run in the child JVMs as they do in the `test` task. Every child JVM runs them, so an agent that writes one output file (JCov, an OpenTelemetry or Datadog agent) has each child write to it, and `-agentlib:jdwp` with a fixed port clashes when `threads` is above 1.

## Do mocking libraries work?

Yes, including inline mocking. Measured on JDK 17 and 21 with MockK 1.14 mocking a static method of a project class:

- Without a `-javaagent`, MockK attaches its agent at runtime in the child JVMs as in the `test` task (JDK 21 prints a dynamic agent loading warning).
- With the agent set on the `test` task and dynamic loading turned off (`jvmArgs '-XX:-EnableDynamicAgentLoading', '-javaagent:<byte-buddy-agent jar>'`), the agent is forwarded and the tests pass in both tasks. Mockito 5's `-javaagent:<mockito-core jar>` setup is forwarded the same way (not measured).

## Does Kotest work?

Yes. Every run gets a fresh class loader, so Kotest would scan the whole classpath for its project config and `@AutoScan` extensions in every run, which on a large classpath takes longer than the preparation limit. When Kotest is on the test runtime classpath, MutKt turns those scans off in its child JVMs (`kotest.framework.classpath.scanning.config.disable`, `kotest.framework.classpath.scanning.autoscan.disable`) and names the project config it finds among the test classes and the class directories of the test runtime classpath in `kotest.framework.config.fqn`. The config scan stays on when there are several project configs or a dependency jar has a class that refers to `AbstractProjectConfig` (a project config held by a dependency, such as a shared test-fixtures jar, cannot be named), and the `@AutoScan` scan stays on when a test class, class directory or dependency jar has an `@AutoScan` class. Kotest's own jars are not searched. A value the `test` task sets for one of these properties is kept.

## JUnit 4: "Cannot create Launcher without at least one TestEngine"

MutKt runs the tests through the JUnit Platform, and a JUnit 4 build (`useJUnit()`, AGP's default) has no Platform engine. Select the vintage engine:

```kotlin
mutkt { engines = setOf("junit-vintage") }
```

MutKt then adds `junit-vintage-engine`, of the JUnit version it ships with, to its test JVMs. `junit:junit` must be on the test runtime classpath: it comes from the project. A project that already has a vintage engine on its test runtime classpath keeps its own. A project with its own JUnit Platform launcher (`junit-platform-launcher` on the test runtime classpath) runs on that Platform, which MutKt's engine may not match, so the task fails until you add `org.junit.vintage:junit-vintage-engine` of your JUnit Platform version to `testRuntimeOnly`.

## Does Robolectric work?

Yes, at the cost of one child JVM per mutant. A child JVM normally runs mutant after mutant, but Robolectric caches its sandbox, with the copy of each project class it instrumented, in JVM-wide statics it never resets, so a reused JVM would test the class it loaded first and give wrong counts. When the test runtime classpath has Robolectric (`org/robolectric/internal/SandboxManager.class`), every run gets a fresh child JVM, which pays the JVM and sandbox start-up each time.

## Why was a class skipped?

MutKt recomputes the stack frames of every class it mutates, which needs the supertypes of the types its branches join. It finds them in the test runtime classpath, the main compile classpath (so `compileOnly` types resolve) and the JDK running Gradle. A type missing from all of them, such as a JDK class newer than the Gradle JVM, makes MutKt skip that class: the build log shows `Skipped <class>: cannot resolve <type> to compute stack frames`, and the class has no mutants in the reports. Every other class is still analysed. Running Gradle on the same JDK as the tests avoids this.

## Android: "MutKt cannot load the Android Gradle plugin's classes"

MutKt compiles against AGP but does not ship it, so its class loader must see the one your build uses. That holds when AGP is declared in the same `plugins { }` block as MutKt or in a parent project's, for example both in the root with `apply false` (see [getting-started.md](getting-started.md#android)). AGP declared only in a module below the one that declares MutKt fails with this message.

## Android: "variant 'release' has no unit tests"

AGP 9 turns on `android.onlyEnableUnitTestForTheTestedBuildType` by default, so only the tested build type (`debug`) has unit tests. To mutate against another build type's tests, add `android.onlyEnableUnitTestForTheTestedBuildType=false` to `gradle.properties`. The message lists the variants that have unit tests; a variant disabled with `enableUnitTest = false` is left out of it too.

## Android: can MutKt run instrumented tests?

No. MutKt runs a variant's local unit tests (`src/test`, on the JVM, Robolectric included). Instrumented tests (`src/androidTest`, `connectedAndroidTest`) need a device or an emulator and are not supported: running them per mutant means one APK install per mutant. See [ADR 0012](adr/0012-android-instrumented-tests.md) for the cost estimate and what would reopen it.

## Android: why are R, BuildConfig or binding classes not mutated?

An Android module starts `excludedClasses` with globs for the classes the build generates (R, BR, BuildConfig, Hilt, Dagger, view and data binding, Compose). The globs go by name, so a class of yours that fits one is skipped too: `**.R`, `**.BR` and `**.BuildConfig` match any class with that simple name, `**.*_*Factory` any class with an underscore that ends in `Factory`, and `**.databinding.*Binding*Impl` such a class in any `databinding` package. `excludedClasses = [...]` replaces the globs; [dsl.md](dsl.md#android-generated-classes) lists them all.

## Where are the reports, and why is there no output?

`build/reports/mutkt/` of the project (of each module, on Android too) holds `mutation-report.html`, `mutation-report.json` (Stryker schema), `mutation-report.sarif` (SARIF 2.1.0) and `mutkt.txt`. An up-to-date run prints nothing: the reports from the last run are still there. `reports.console = false` keeps the summary out of the build log; the files are written on every run that gets past the baseline.

## Why doesn't `check` run it?

A run executes the test suite once per mutant, which is too slow for every build. Opt in with `tasks.check { dependsOn("mutkt") }`.
