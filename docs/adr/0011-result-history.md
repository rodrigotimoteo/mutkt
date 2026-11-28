# 0011 Result history

## Context

Every run evaluated every mutant, so a CI build paid the whole analysis for a one-line change. ROD-266 built the reuse of earlier results in `mutkt-core` (`ResultHistory` port, per-mutant keys, versioned JSON format) and ROD-267 wires it into the Gradle task.

## Decision

The task keeps the KILLED, SURVIVED and TIMEOUT results of its last run in `historyFile` (default `build/mutkt/history.json`). ERROR may be transient and NO_COVERAGE comes from every baseline, so neither is kept. A mutant reuses its stored result when two digests match:

- The run's **fingerprint**: MutKt's own jars by content (not the version string, so a snapshot that changes an operator counts), the operators, engines, tag filters, `timeoutMs`, the resolved `threads` (parallel load decides whether a slow mutant ends in TIMEOUT), the `test` task's JVM arguments, working directory and Java runtime (vendor and full version), the test runtime entries that are not class directories (jars and resource directories, by absolute path and content, in classpath order; a file whose size and modification time match the last run keeps the digest stored in the history file, so an unchanged classpath is not read again), and the **helper** classes: test classes that neither declare a JUnit test method (`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate`, JUnit 4 `@Test`) nor inherit one from a test-source supertype, and the classes of other source sets loaded next to the main classes (test fixtures). Any change drops the whole history.
- The mutant's **key**: its id and mutated bytes, the bytes of its covering test classes with their nested classes, their enclosing classes (a static nested class can be a root test class) and their supertypes in the test sources (read with ASM in `mutkt-gradle`), and of every main class those test classes reached in the baseline, every unprobed class and every class reached outside a test class (ADR 0010). A mutant that runs the whole suite has every test and main class in its key.

The history is written after the run, replacing the file through a temporary file and an atomic move, and keeps the stored entries of mutants this run did not evaluate (a narrower `targetClasses`) while the fingerprint matches. A file of another format version is ignored; a file that cannot be parsed fails the task with `The MutKt result history <path> is corrupt (<detail>): delete it and run again`, since silently dropping it would hide the cause.

`historyFile` is a `@LocalState` of the task, never an output: the task stays non-cacheable, a build cache never restores a history, and the file does not make the task out of date. CI keeps it between runs with its own cache (getting-started.md).

## Consequences

- Reach comes from the baseline: a mutant that sends a test into a class the baseline never reached is keyed without that class, so a change there alone can leave a stale result. The blind spots of reach (ADR 0010: static field reads, inlined constants, `inline` bodies) apply too.
- A test class A that calls helper code in another test class B that has tests of its own keys on A only: B is in the keys of the mutants B covers, so a change to B's helper code can leave A's results stale. Helper classes without test methods are safe: they are in the fingerprint.
- The daemon's environment variables, which the test JVMs inherit, are not in the fingerprint by design: CI sets run-specific ones (build numbers, tokens) on every run, which would drop the history each time. A test whose outcome depends on one can keep a stale result.
- A classpath file rewritten with the same size and modification time (in milliseconds) keeps its old digest.
- A custom meta-annotation for test methods is not recognised, so its class counts as a helper and every change to it drops the whole history: slower, never stale.
- Absolute classpath paths make the fingerprint machine-specific: a history moves between CI runs on the same runner image, not between a laptop and CI.
- Hashing the test runtime classpath on every run costs a read of every jar; correctness first (ROD-135 may cache it).
- Status: accepted under owner autopilot, pending owner confirmation.
