# Security Policy

## Reporting a vulnerability

Do not open a public issue for a security vulnerability.

Report it privately through GitHub private vulnerability reporting: [open an advisory](https://github.com/rodrigotimoteo/mutkt/security/advisories/new). We respond within 48 hours and agree on a fix and disclosure date with you before anything is made public.

## Supported versions

Only the latest release gets security fixes. The 0.3.x line and earlier are superseded by the rebuild and no longer supported.

## What the project does

- CodeQL and a verified-secret scan (TruffleHog) run on every push and pull request, and weekly (`.github/workflows/security.yml`).
- Dependabot proposes Gradle and GitHub Actions updates weekly; versions are pinned in `gradle/libs.versions.toml`.
- Releases are published only by `.github/workflows/release.yml` from a `v*` tag, after approval of the `release` environment, and every artifact is GPG-signed (see `docs/release.md`).
