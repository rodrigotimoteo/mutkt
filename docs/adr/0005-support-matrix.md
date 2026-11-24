# 0005 Support matrix

## Context

MutKt must state which JDKs, Gradle versions and JUnit engines it supports (ROD-85).

## Decision

JDK 17 and 21. Gradle 8.x. Jupiter engine by default; vintage and suite engines are opt-in.

## Consequences

- Vintage and suite support are not part of the default setup.
