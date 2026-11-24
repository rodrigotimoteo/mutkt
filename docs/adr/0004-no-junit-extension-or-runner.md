# 0004 No JUnit extension API or runner module

## Context

The question is whether MutKt should add a JUnit extension API or a runner module (ROD-84).

## Decision

No JUnit extension API and no runner module. Existing JUnit tests run unchanged against each mutant.

## Consequences

- `mutkt-junit` depends only on the JUnit Platform Launcher.
- No runner module is published.
