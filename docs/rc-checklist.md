# Release candidate checklist: 0.4.0-rc.1

Every item must be green before the tag is pushed. **OWNER** items need repository settings, secrets or a release only the owner can do. State as of 2026-11-25, JDK 21 on macOS (11 cores), from the RC branch on top of `rebuild/v1` at `9ba2082`.

## Repository checks

| Item | How it is verified | State |
|---|---|---|
| Lint, tests, build | `./gradlew ktlintCheck detekt build koverVerify --offline`; CI jobs `Lint`, `Test (JDK 17, 21)`, `Build and coverage gate` | Green locally (JDK 21); CI on the PR |
| Architecture tests | `ArchitectureTest` in `mutkt-core` (part of `test`) | Green: 7/7 |
| Coverage gate (85% line per published module) | `./gradlew koverVerify`; CI `Build and coverage gate` | Green: all six modules |
| Dogfood scores | `./gradlew -p tools/dogfood mutkt --continue` (see [dogfood.md](dogfood.md)); CI `dogfood` | Green: `mutkt-core` 83.0% (100 mutants, gate 75), `mutkt-asm` 96.8% (63 evaluated, 170 omitted by the cap, gate 90) |
| TestKit scenarios | `e2eTest` (Gradle 8.10, in `check`); `./gradlew :mutkt-gradle:matrixTest -Pmutkt.gradleVersions=8.14.3`; CI `testkit-matrix` | Green: e2eTest; matrixTest 13/13 on 8.14.3 |
| Forked executor on a second JUnit Platform | `./gradlew :mutkt-junit:platformCompatTest`; CI `platform-compat` | CI only (needs the network); not run locally |
| Parity: zero undocumented differences | `ParityTest` (every legacy-only or new-only site matches a deviation in `Deviations.kt`, and no deviation is unused) and `ParityDocTest` (the table in [parity.md](parity.md) matches it) in `mutkt-asm` | Green: 19/19 and 1/1 |
| File size limits (main ≤ 300 lines, tests ≤ 400) | Script below | Green: 222 files, none over |
| No TODO/FIXME without an issue | `git grep -n -I -E "TODO\|FIXME" \| grep -v -E "ROD-[0-9]+"` | Green: no TODO or FIXME in the tree |
| License | Apache 2.0 `LICENSE` at the root, the same license in every POM (`mutkt.publishing`), linked from the README. Source files carry no per-file header, and none does | Consistent |
| `SECURITY.md` current | Reporting channel, supported versions and the measures CI actually runs | Updated for the rebuild |
| Publication contents | `tools/release/dry-run.sh 0.4.0-rc.1` (an unsigned publication without a key, then a signed one); CI `Publish dry run` | Green: unsigned publication verified; 31 signed files, 7 POMs |
| Local consumer rehearsal | [rc-evidence/local-consumer-rehearsal.md](rc-evidence/local-consumer-rehearsal.md) | Green: both scenarios match their predictions |
| Reviewer pass over the whole tree | `reviewer` agent on the tree; findings fixed or recorded | Pending |
| CHANGELOG | `[Unreleased]` moved under `0.4.0-rc.1` ([release.md](release.md), step 1) | Done (heading marked unreleased; add the date when tagging) |

File sizes:

```bash
git ls-files '*.kt' '*.kts' '*.java' | while read f; do
  n=$(wc -l < "$f")
  case "$f" in */src/test/*|*/src/*Test/*) lim=400;; *) lim=300;; esac
  [ "$n" -gt "$lim" ] && echo "OVER $n/$lim $f"
done
```

## Owner actions

| Item | How it is verified | State |
|---|---|---|
| Secrets `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD`, `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` | Repository settings, Secrets (see [release.md](release.md#secrets-to-be-configured-by-the-owner)); Central namespace verified; public key on `keys.openpgp.org` | Done: the rc.1 release signed and published to Central |
| Secrets `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET` | Repository settings, Secrets | Set; first used by the next tag (see ROD-206) |
| `release` environment | Settings, Environments | Done: deployments limited to `v*` tags; no required reviewers (single maintainer) |
| `v*` tag protection | Settings, Rules | Done: ruleset "Protect release tags" blocks deleting or moving `v*` tags |
| GitHub private vulnerability reporting enabled (the channel `SECURITY.md` names) | Settings, Code security | Done |
| Tag `v0.4.0-rc.1` pushed on a commit that passed CI; release run approved | `release.yml` run: Central, Plugin Portal, GitHub release | Done: [run 45780375244](https://github.com/rodrigotimoteo/mutkt/actions/runs/45780375244) on `6b39540`; Central and GitHub pre-release published. The Plugin Portal upload was skipped by a faulty check (ROD-206, fixed); the Portal still serves the plugin by redirecting to Central |
| Public consumer verification | The local rehearsal repeated after publication: a clean external project, fresh dependency cache, the documented public repositories only, both scenarios. Keep the release run link, coordinates, build output and report summaries in `docs/rc-evidence/`; record any stage still unresolved after propagation | Green: [rc-evidence/public-consumer-check.md](rc-evidence/public-consumer-check.md), both scenarios match their predictions |

## Findings

- Fixed: `publishToMavenLocal` without `signingInMemoryKey` failed on `:mutkt-gradle:signMutktPluginMarkerMavenPublication` ("no configured signatory"): plugin-publish signs its own publications whenever `signing` is applied. `signing.isRequired` now follows the key, and `dry-run.sh` checks an unsigned publication first.
- The dogfood table in [dogfood.md](dogfood.md) is refreshed with these numbers; the change is below the 5-point ratchet step, so the thresholds stay.
