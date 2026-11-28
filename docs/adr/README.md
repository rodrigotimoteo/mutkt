# Architecture decision records

One file per decision: `NNNN-title.md` with Context, Decision, Consequences. Source of the question and recommendation is the Linear decision issue.

| ADR | Topic | Linear | Status |
|---|---|---|---|
| 0001 | Package name, coordinates, plugin id | ROD-81 | accepted (package com.github…, group io.github…) |
| 0002 | Statuses and score | ROD-82 | accepted |
| 0003 | Report formats | ROD-83 | accepted |
| 0004 | No test-side API / runner module | ROD-84 | accepted |
| 0005 | Support matrix (JDK, Gradle, engines) | ROD-85 | accepted |
| 0006 | Android design | ROD-125 | accepted (2026-11-26) |
| 0007 | KMP scope | ROD-136 | later |
| 0008 | Forked JVM pool instead of WorkerExecutor | ROD-104 | accepted (autopilot, owner to confirm) |
| 0009 | Single release pipeline | ROD-121 | accepted (autopilot, owner to confirm) |
| 0010 | Per-test coverage selects each mutant's tests; amends NO_COVERAGE of 0002 | ROD-237 | accepted (autopilot, owner to confirm) |
| 0011 | Result history: reuse unchanged mutants' results between runs | ROD-267 | accepted (autopilot, owner to confirm) |
