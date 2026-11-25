# 0009 Single release pipeline

## Context

The legacy repository had two workflows that could both publish, a documented Nexus plugin that the build did not use, and a release that needed manual retries (ROD-121).

## Decision

- One workflow, `release.yml`, on `v*` tags: full CI, then publish behind the `release` environment.
- Plain `maven-publish` and `signing` (in-memory keys from secrets) to Maven Central via the Central Portal staging API; `com.gradle.plugin-publish` for the Gradle Plugin Portal; GitHub release with generated notes.
- GitHub Packages is dropped: Central and the Plugin Portal are the only distribution channels.
- Every stage checks the target repository first, so a re-run completes missing stages and never replaces an artifact.
- The tag is the version source (`-Pmutkt.version`).

## Consequences

- No second workflow may publish; dry runs use `publishToMavenLocal` with a throwaway key.
- Operating procedure and secrets: [docs/release.md](../release.md).
