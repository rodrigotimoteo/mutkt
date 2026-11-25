# DSL reference

```kotlin
plugins { id("io.github.rodrigotimoteo.mutkt") }
mutkt { targetClasses.add("com.example.*") }
```

Requires the Java or Kotlin JVM plugin. A test fails when this table drifts from the extension.

| Property | Type | Default | Meaning |
|---|---|---|---|
| `targetClasses` | List<String> | empty (all) | Class globs to mutate |
| `excludedClasses` | List<String> | empty | Class globs never mutated |
| `excludedMethods` | List<String> | empty | `method` or `pkg.Class#method` entries to skip |
| `operators` | Set<String> | empty (default operators) | Operator ids to enable, from [operators.md](operators.md); empty runs the six default-on operators, a non-empty set runs exactly those ids (list every id to run all); an unknown id fails the run and lists the valid ones |
| `threads` | Int | unset (available processors, resolved at run time) | Parallel test runs |
| `timeoutMs` | Long | 10000 | Per-mutant deadline in milliseconds |
| `maxMutantsPerClass` | Int | unset (unlimited) | Cap on mutants per class |
| `failBelowScore` | Int | unset (no gate) | Minimum score in percent (0..100); the build fails below it |
| `engines` | Set<String> | `junit-jupiter` | JUnit Platform engine ids to run (`junit-jupiter`, `junit-vintage`); every id must be on the test runtime classpath, otherwise every mutant ends in ERROR and the run fails |
| `includeTags` | Set<String> | empty | Only run tests matching one of these JUnit tag expressions (`fast`, `a & b`) |
| `excludeTags` | Set<String> | empty | Skip tests matching one of these JUnit tag expressions (`slow`, `!fast`) |

`reports { }`:

| Property | Type | Default | Meaning |
|---|---|---|---|
| `console` | Boolean | true | Print the summary to the build log |

## Running

`./gradlew mutkt` (group `verification`) mutates the `main` classes and runs the `test` classes against each mutant in child JVMs that copy the `test` task's JVM arguments, system properties, working directory and Java launcher (the environment is inherited from the Gradle daemon). The task is up to date until the classes, the test runtime classpath, the sources or these settings change, and it supports the configuration cache. An up-to-date run prints no summary. `-javaagent` arguments of the `test` task are not forwarded, so coverage agents never see mutated classes. The summary is also written to `build/reports/mutkt/mutkt.txt` (the only report file so far). With `failBelowScore` set, the task fails with `Mutation score 62.50% is below the threshold of 80.00% (report: <path>)` when the score is lower; a score equal to the threshold passes, a value outside 0..100 fails the build, and a failed task is never up to date, so it fails again until the score or the setting changes. When no mutant is scorable (none generated, or all errored) there is no score: the build succeeds with a warning and `failBelowScore` is not applied. The tag filters and engines apply to every test run, so a mutant whose only killing tests are excluded survives, and one with no test left to run reports no coverage. When every mutant ends in ERROR (a wrong engine id or a broken classpath, for instance) the task fails with `All N mutants ended in ERROR; check engines/classpath` after writing the report.

The child JVMs run the project's own JUnit Platform when its test runtime classpath has `junit-platform-launcher`, so a project on a newer Jupiter works; without it they fall back to the Platform bundled with the plugin (1.10.2), which may be older than the project's engine. Recommended: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` (it needs the JUnit BOM or an explicit version; Gradle 8 injects the launcher into the `test` task only, so it is often absent from the classpath MutKt sees).

It is not part of `check`. To opt in:

```kotlin
tasks.check { dependsOn("mutkt") }
```
