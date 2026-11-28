# 0005 Support matrix

## Context

MutKt must state which JDKs, Gradle versions and JUnit engines it supports (ROD-85).

## Decision

JDK 17 and 21. Gradle 8.x. Jupiter engine by default; vintage and suite engines are opt-in.

## Consequences

- Vintage and suite support are not part of the default setup.

## Amended 2026-11-26

Gradle 8.10 or later, including 9.x. Tested on 8.10, 8.14.3 and 9.9.2.

## Amended 2026-11-27

Owner decision 2026-11-27: the minimum Gradle is 8.11, and Gradle 8.10 is no longer supported from 0.6.0. The published modules are compiled against the Kotlin 2.0 API, since Kotlin 2.4 cannot target an older one, and Gradle loads plugins on its own embedded stdlib. On Gradle 8.10 (stdlib 1.9) any 2.0-only stdlib call would fail at runtime, which tests cannot rule out, so the minimum moves to 8.11, the first Gradle with Kotlin 2.0. Tests still pass on 8.10 today: it is unsupported, not known broken. Tested on 8.11.1, 8.14.3 and 9.9.2.
