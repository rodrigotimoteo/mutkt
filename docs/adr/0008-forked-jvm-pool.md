# 0008 Forked JVM pool instead of WorkerExecutor

## Context

ROD-104 asked for `WorkerExecutor.processIsolation`. The Gradle 8.10 Worker API cannot kill a running worker: `WorkQueue` has only `submit` and `await`, and `WorkAction.execute` returns void (checked with javap on gradle-api-8.10.jar). A hung mutant (infinite loop) therefore cannot be stopped.

## Decision

`RunMutationAnalysis` stays in the Gradle daemon. A plugin-owned pool of child JVMs, started with `ProcessBuilder` from the task action, runs mutants through a length-prefixed frame protocol. The child answers a run with a `Started` frame once classes are loaded and linked and the tests discovered (discovery of all test classes counts against the preparation limit, so a stall there is caught by it; the plans are held until the run ends, a known cost until ROD-135), then with the result. The parent calls `destroyForcibly()` on a child and starts a replacement when it is silent for deadline plus grace after `Started` (TIMEOUT, counted as detected, ADR 0002) or for the preparation limit before it (60 s by default; the run throws, so the mutant is ERROR and earns no detection credit). JVM args, system properties and launcher are copied from the Gradle `Test` task.

## Consequences

- Criterion "daemon memory unaffected" is verified as child pid != daemon pid.
- Memory cost is N children times the test heap.
- Status: accepted under owner autopilot, pending owner confirmation. The change is reversible and sits behind the `TestExecutor` port.
