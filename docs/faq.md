# FAQ

## What counts as killed?

A mutant is KILLED when a test or a container (a test class, its `@BeforeAll`) fails while the mutant is loaded: a failed assertion and an exception thrown by the mutated code both count. Aborted tests (failed assumptions) and disabled tests do not. The test classes run one at a time, sorted by name, and the run stops at the first class with a failure; the report names that first failing test (`killedBy` in the JSON report).

The other statuses:

| Status | Meaning | Score |
|---|---|---|
| SURVIVED | Every selected test passed | not detected |
| TIMEOUT | The tests did not finish within `timeoutMs` | detected |
| NO_COVERAGE | No test ran at all, for example because `includeTags` or `engines` select none | not detected |
| ERROR | The mutant could not be loaded or run (a `VerifyError`, a crashed child JVM) | left out |

MutKt does not measure coverage: every selected test runs against every mutant, so NO_COVERAGE never means "no test reaches this line". Score = (KILLED + TIMEOUT) / (all mutants − ERROR).

## Why does TIMEOUT count as detected?

A mutant that makes a test run past the deadline usually turned a loop or a recursion endless: the tests noticed a change, they just never finished. The deadline (`timeoutMs`, 10 s by default) is per mutant and starts when the tests start, after class loading. A child JVM that passes it is killed and replaced, so an endless loop does not hold a thread.

If many mutants end in TIMEOUT, the suite is too slow for the deadline: raise `timeoutMs`. Otherwise a slow or busy machine can inflate the score. The baseline run gets 5 times `timeoutMs`, since its JVM starts cold.

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

## Why are `-javaagent` arguments not forwarded?

The child JVMs copy the `test` task's JVM arguments except `-javaagent`, so agents of the `test` task (coverage tools such as JaCoCo, other instrumentation) never run on mutated classes.

## Do mocking libraries work?

Agent-based mocking is not supported yet: a mocking library that the `test` task loads through `-javaagent` is dropped in the child JVMs like any other agent, so tests that depend on it may fail in the baseline run.

## Where are the reports, and why is there no output?

`build/reports/mutkt/` holds `mutation-report.html`, `mutation-report.json` (Stryker schema) and `mutkt.txt`. An up-to-date run prints nothing: the reports from the last run are still there. `reports.console = false` keeps the summary out of the build log; the files are always written.

## Why doesn't `check` run it?

A run executes the test suite once per mutant, which is too slow for every build. Opt in with `tasks.check { dependsOn("mutkt") }`.
