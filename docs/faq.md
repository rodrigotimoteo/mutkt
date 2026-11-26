# FAQ

## What counts as killed?

A mutant is KILLED when a test or a container (a test class, its `@BeforeAll`) fails while the mutant is loaded: a failed assertion and an exception thrown by the mutated code both count. Aborted tests (failed assumptions) and disabled tests do not. The test classes run one at a time, sorted by name, and the run stops at the first class with a failure; the report names that first failing test (`killedBy` in the JSON report).

A mutant that exits its child JVM once the tests have started is KILLED too, as in PIT: a `System.exit` or an `OutOfMemoryError` (JUnit does not catch it). `killedBy` then reads `JVM exit 7` (the exit code) or `OutOfMemoryError`. A `StackOverflowError` in a test is an ordinary test failure. These stay ERROR: a child ended by a signal (exit code above 128, such as the OS out-of-memory killer's 137), reported as `killed by signal 9`, since that is usually the machine rather than the mutant; and a child that dies earlier, while it loads the classes and discovers the tests. If the baseline run exits the JVM, the task fails with `the test JVM exited with code 7 on the original classes`.

The other statuses:

| Status | Meaning | Score |
|---|---|---|
| SURVIVED | Every selected test passed | not detected |
| TIMEOUT | The tests did not finish within the mutant's deadline | detected |
| NO_COVERAGE | No test ran at all, for example because `includeTags` or `engines` select none | not detected |
| ERROR | The mutant could not be loaded or run (a `VerifyError`, a child JVM that died before the tests started) | left out |

MutKt does not measure coverage: every selected test runs against every mutant, so NO_COVERAGE never means "no test reaches this line". Score = (KILLED + TIMEOUT) / (all mutants − ERROR).

## Why does TIMEOUT count as detected?

A mutant that makes a test run past the deadline usually turned a loop or a recursion endless: the tests noticed a change, they just never finished. The deadline is per mutant and starts when the tests start, after class loading. It is 4 times how long the tests ran on the original classes (the baseline run) plus 4 s, at most `timeoutMs` (60 s by default). A child JVM that passes it is killed and replaced, so an endless loop does not hold a thread.

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

One limitation: MockK's string form `mockkStatic("com.example.Prices")` fails with `ClassNotFoundException` in the child JVMs, because MockK resolves the name through its own class loader, which does not see the project's classes there. Use the class form, `mockkStatic(Prices::class)`.

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

No. MutKt runs a variant's local unit tests (`src/test`, on the JVM, Robolectric included). Instrumented tests (`src/androidTest`, `connectedAndroidTest`) need a device or an emulator and are not supported.

## Android: why are R, BuildConfig or binding classes not mutated?

An Android module starts `excludedClasses` with globs for the classes the build generates (R, BR, BuildConfig, Hilt, Dagger, view and data binding, Compose). The globs go by name, so a class of yours that fits one is skipped too: `**.R`, `**.BR` and `**.BuildConfig` match any class with that simple name, `**.*_*Factory` any class with an underscore that ends in `Factory`, and `**.databinding.*Binding*Impl` such a class in any `databinding` package. `excludedClasses = [...]` replaces the globs; [dsl.md](dsl.md#android-generated-classes) lists them all.

## Where are the reports, and why is there no output?

`build/reports/mutkt/` of the project (of each module, on Android too) holds `mutation-report.html`, `mutation-report.json` (Stryker schema) and `mutkt.txt`. An up-to-date run prints nothing: the reports from the last run are still there. `reports.console = false` keeps the summary out of the build log; the files are written on every run that gets past the baseline.

## Why doesn't `check` run it?

A run executes the test suite once per mutant, which is too slow for every build. Opt in with `tasks.check { dependsOn("mutkt") }`.
