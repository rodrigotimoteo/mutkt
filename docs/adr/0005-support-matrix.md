# 0005 Support matrix

## Context

MutKt must state which JDKs, Gradle versions and JUnit engines it supports (ROD-85).

## Decision

JDK 17 and 21. Gradle 8.x. Jupiter engine by default; vintage and suite engines are opt-in.

## Consequences

- Vintage and suite support are not part of the default setup.

## Amended 2026-11-26

Gradle 8.10 or later, including 9.x. Tested on 8.10, 8.14.3 and 9.9.2.
