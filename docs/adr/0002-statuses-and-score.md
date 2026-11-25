# 0002 Statuses and score

## Context

Mutation results need a fixed status set and one score formula (ROD-82). A mutant can also end the child JVM that runs its tests (`System.exit`, an `OutOfMemoryError`), which is a real behavior change but not a test failure (ROD-201).

## Decision

Statuses are `KILLED`, `SURVIVED`, `TIMEOUT`, `NO_COVERAGE` and `ERROR`. Score = (KILLED + TIMEOUT) / (total - ERROR). The score is null when nothing is scorable. Scores assume the baseline run of the tests on the original classes was green; a red baseline stops the run before any mutant is scored.

A child JVM that exits after it reported `Started` (ADR 0008) is KILLED, as PIT counts memory errors: `killedBy` is `JVM exit <code>`, or `OutOfMemoryError` when that error ended the child. An exit code above 128 means a signal ended the child (the OS out-of-memory killer, a user); that is usually the machine, not the mutant, so it is ERROR with the cause `killed by signal <N>`. A child that dies or stalls before `Started` (start-up, class loading and linking, test discovery), a protocol error and a class the JVM refuses (`LinkageError`) stay ERROR: they say nothing about the tests.

## Consequences

- `mutkt-core` domain `Status` and `Score` hold these rules; the forked executor (`mutkt-junit`) reports a death after `Started` as a kill and replaces the child.
- A mutant killed by a JVM exit names no test; any `System.exit` reached while the tests run counts as detected.
- A baseline whose child exits fails the run with `the test JVM exited with code <N> on the original classes`.
- Reports (ADR 0003) use the same statuses and score.
