# 0001 Package name, coordinates and plugin id

## Context

Maven Central rejects `com.github.*` group ids, so the Maven coordinates cannot use the code package (ROD-81).

## Decision

Code package `com.github.rodrigotimoteo.mutkt`. Maven group and plugin id `io.github.rodrigotimoteo`.

## Consequences

- Package and group intentionally differ.
- Plugin id and published coordinates use `io.github.rodrigotimoteo`.
