# Releasing

One workflow publishes: [release.yml](../.github/workflows/release.yml), triggered by a `v*` tag. Nothing else can publish. GitHub Packages is not used (ADR 0009).

## Cut a release

1. Move the `[Unreleased]` notes in [CHANGELOG.md](../CHANGELOG.md) under the new version on `master` and merge through a PR. That section, without its heading, becomes the GitHub release notes, so write it for users. `tools/release/notes.sh <version>` prints it; the `publish` job runs it before any upload and fails if the section is missing or empty (`tools/release/notes-test.sh` checks it in CI).
2. Run the end-to-end suite on that commit: `tools/e2e/run.sh` (local only, see [tools/e2e/README.md](../tools/e2e/README.md)). Every repo must pass; a changed result is either a regression to fix or an expectation to update in the same PR, with the reason.
3. Tag the merged commit and push the tag: `git tag v0.4.0 && git push origin v0.4.0`. The tag is the only version source: the workflow passes `-Pmutkt.version=<tag without v>`; local builds stay `0.0.0-SNAPSHOT`. Tags must look like `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-prerelease`.
4. The `verify` job runs the full CI. The `publish` job then waits for approval of the `release` environment.
5. After approval the job runs these stages in order. Each one first asks the target repository whether it already has the version and skips itself if so:
   1. Maven Central: `./gradlew publish` signs and uploads annotations, core, asm, junit, report, gradle and the plugin marker to the Central Portal staging API, then `tools/release/central-release.sh` hands the upload over for automatic publishing. The job waits up to 45 minutes until Central serves all POMs.
   2. Gradle Plugin Portal: `tools/release/publish-plugin.sh` runs `./gradlew :mutkt-gradle:publishPlugins`, then waits up to 15 minutes for the marker. Only a 200 from the Portal, or a 303 to its CDN `plugins-artifacts.gradle.org`, counts: it answers 303 to Central for artifacts it does not host, and Central has the marker after stage one. `tools/release/published-test.sh` checks this against a local server. The plugin declares Configuration Cache compatibility (`compatibility { features { ... } }` in `mutkt-gradle/build.gradle.kts`), which the Portal shows as a badge; `verify-publication.sh` checks it in the plugin descriptor.
   3. GitHub release: `gh release create --notes-file` with the version's CHANGELOG section from `tools/release/notes.sh` (`-` in the version marks it a pre-release).

The `publish` job rebuilds from the tagged commit with the tag version; the artifacts are not the ones `verify` tested, only the same sources at a different version. Keep tags on commits that passed CI.

Plugin id `io.github.rodrigotimoteo.mutkt`, group `io.github.rodrigotimoteo` (ADR 0001).

## Secrets (TO BE CONFIGURED BY THE OWNER)

Repository secrets only; no value ever goes in a file. All are read by the `publish` job and exposed per step, never to the verify job. `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD`, `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` already exist in the repo (used by 0.3.3).

| Secret | Used for |
|---|---|
| `GPG_SIGNING_KEY` | ASCII-armored private GPG key (`gpg --armor --export-secret-keys <id>`). Its public key must be on a keyserver (`keys.openpgp.org`) so Central can check signatures. |
| `GPG_SIGNING_PASSWORD` | Passphrase of that key; empty if the key has none. |
| `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` | A Central Portal user token (central.sonatype.com, Account, Generate User Token): the two halves of the token, not the login. The token is used both for the Gradle upload and for the finalize call. The namespace `io.github.rodrigotimoteo` must be verified in the Portal first. |
| `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET` | API key and secret from plugins.gradle.org (Profile, API Keys). |

Also create the `release` environment (Settings, Environments) and add required reviewers: that is the approval gate. Restrict who can push `v*` tags (tag protection rule).

## Dry run

Every PR and branch push runs the `Publish dry run` CI job. Locally:

```bash
tools/release/dry-run.sh 0.4.0
```

It first runs `publishToMavenLocal` without a key and checks that every module and the plugin marker publish unsigned (ordinary builds must not need a key). Then it generates a throwaway GPG key, runs `publishToMavenLocal` into a temporary repository (`-Dmaven.repo.local`), and `verify-publication.sh` checks for the six published modules and the plugin marker: all files signed with a valid signature, sources and javadoc jars present, Dokka HTML (`index.html`) inside the javadoc jars of `mutkt-annotations` and `mutkt-gradle`, POM name, description, url, license, scm and developers set, and the plugin descriptor inside the plugin jar. Signing happens only when the `signingInMemoryKey` Gradle property is set, so ordinary builds are unsigned.

## Rehearsal (before the first public release; tracked in ROD-124)

Not yet run. The build side supports it with `-Pmutkt.publishUrl=<url>`, which replaces the Central staging URL (use a disposable `file:` or local HTTP repository), and the `CENTRAL_URL` and `PLUGIN_PORTAL_URL` variables of `published.sh`.

1. Publish the candidate: `./gradlew publish -Pmutkt.version=0.4.0 -Pmutkt.publishUrl=file:///tmp/rehearsal-repo` with a throwaway signing key.
2. In a separate Kotlin/JVM project, with a fresh `GRADLE_USER_HOME` and no included build, apply `id("io.github.rodrigotimoteo.mutkt") version "0.4.0"` via the plugins DSL, with only the rehearsal repository (plus Central and the Plugin Portal for third-party dependencies) in `pluginManagement` and `dependencyResolutionManagement`. Use a fixture whose killed and surviving mutants were predicted by hand, run `test` and the mutation task, and compare the outcomes.
3. Recovery: let stage one succeed and make stage two fail (for example a wrong `GRADLE_PUBLISH_KEY`). Re-run the workflow (Re-run failed jobs). `published.sh central` finds the version, so Central is skipped and only the failed stages run. Archives are reproducible, so `<artifact>.jar.sha1` of the published file equals a local build of the same commit; compare them before trusting a published version. Never delete or overwrite a published version.

## Rollback and failure notes

- Central and the Plugin Portal do not allow replacing a version. A bad release is fixed with the next version (`0.4.0-rc.2`); mark the broken one deprecated in the GitHub release notes.
- Central stage timed out waiting: open the Portal deployments page before re-running. A re-run while the first deployment is still validating sees no version on Central and uploads again; the Portal then reports a duplicate or conflicting deployment. That is noise, not damage (nothing is replaced), but drop the extra deployment in the Portal UI. Better: let the first deployment finish, then re-run to complete the later stages.
- A Portal deployment that failed validation can be dropped in the Portal UI; then re-run.
- The first upload of a new plugin id waits for manual approval by Gradle, so the 15-minute wait for the marker fails. When the `publishPlugins` output says "submitted for approval", `publish-plugin.sh` skips the wait with a `::warning::` and the job goes on to the GitHub release (`tools/release/publish-plugin-test.sh` checks this in CI). If the Portal words it differently, the wait fails and the GitHub release is missing: create it by hand with `gh release create` as in stage three, or re-run the job after approval, which skips the stages that are done.
- A tag pushed by mistake: reject the approval of the `release` environment, then delete the tag. Nothing has been published before the approval.
