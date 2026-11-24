# 0008 Forked JVM pool instead of WorkerExecutor

## Context

ROD-104 asked for `WorkerExecutor.processIsolation`. The Gradle 8.10 Worker API cannot kill a running worker: `WorkQueue` has only `submit` and `await`, and `WorkAction.execute` returns void (checked with javap on gradle-api-8.10.jar). A hung mutant (infinite loop) therefore cannot be stopped.

## Decision

`RunMutationAnalysis` stays in the Gradle daemon. A plugin-owned pool of child JVMs, started with `ProcessBuilder` from the task action, runs mutants through a length-prefixed frame protocol. The parent calls `destroyForcibly()` on a child after deadline plus grace and starts a replacement. JVM args, system properties and launcher are copied from the Gradle `Test` task.

## Consequences

- Criterion "daemon memory unaffected" is verified as child pid != daemon pid.
- Memory cost is N children times the test heap.
- Status: accepted under owner autopilot, pending owner confirmation. The change is reversible and sits behind the `TestExecutor` port.
