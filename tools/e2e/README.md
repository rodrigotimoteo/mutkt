# End-to-end suite

Runs MutKt, built from this working tree, on real open-source repos pinned in `corpus.json` and checks each mutation report against `expectations/<name>.json`. Local only: nothing here runs in CI. Run it before a release (see [release.md](../../docs/release.md)).

```bash
tools/e2e/run.sh                     # every repo in tools/e2e/corpus.json
tools/e2e/run.sh <name>              # one repo
tools/e2e/run.sh --corpus <file> [<name>]
tools/e2e/selftest/run.sh            # the runner's own test, on a local fixture
```

Needs macOS (`/usr/libexec/java_home` picks each repo's `jdk`), git, `python3`, every JDK the corpus names and, for Android repos, an Android SDK (`ANDROID_HOME`) and the network.

## What a run does

1. Publishes the plugin and its modules at version `0.0.0-e2e` to `build/e2e-repo` with the repository's own wrapper, unsigned.
2. For each repo: fetches its pinned `commit` into `~/.cache/mutkt-e2e/repos/<name>` (`MUTKT_E2E_CACHE` moves the cache), checks it out over any local changes and removes untracked files; ignored files such as `build/` stay, so reruns are incremental.
3. Runs `<module>:mutkt` with the repo's own `gradlew`, `JAVA_HOME` set from `jdk` and the init script `mutkt-e2e.init.gradle`. Gradle's output goes to `~/.cache/mutkt-e2e/logs/<name>.log`.
4. Checks `<module dir>/build/reports/mutkt/mutation-report.json` (the module path with `:` read as `/`) with `e2e.py check`.
5. Prints a table with one PASS or FAIL line per repo and the broken expectations under it, and exits 1 when any repo fails.

## Applying the plugin

The init script adds `build/e2e-repo`, the Plugin Portal, Google and Maven Central to `pluginManagement`. Then, by the entry's `apply`:

- `init-script`: adds `mutkt-gradle` to the root project's buildscript classpath and applies MutKt to `module` once its Java or Android plugin is applied.
- `patch`: applies `patch` (a path relative to the corpus file) with `git apply`; the patch adds `id("io.github.rodrigotimoteo.mutkt") version "0.0.0-e2e"` to the module's `plugins { }` block.

Both modes then set the entry's `mutkt` values (`targetClasses`, `threads`, `timeoutMs`), `variant`, and `engines` from `tests`: `jupiter` is `junit-jupiter`, `vintage` and `robolectric` are `junit-vintage`, `kotest` is `kotest`.

MutKt sees AGP's classes only when AGP is declared in the same or a parent `plugins { }` scope. Init-script mode puts MutKt in the root's class loader, so it works when the root declares AGP (`alias(libs.plugins.android.application) apply false`, as most Android projects do) or the module is the root. When only the module declares AGP, the run fails with `MutKt cannot load the Android Gradle plugin's classes` and the table says so: switch that entry to patch mode. Make the patch from a clean checkout of the pinned commit (`git diff > tools/e2e/patches/<name>.patch`).

## Expectations

`e2e.py check` fails a repo when:

- the number of mutants is outside `mutants.min..max`;
- the score, (Killed + Timeout) / (all − RuntimeError) as in the console summary, is outside `score.min..max`, or there is none;
- more than `maxError` mutants ended in ERROR (`RuntimeError`);
- an id in `operators` produced no mutant;
- a glob in `mustNotContain` (MutKt's class globs: `*` within a package segment, `**` across them) matches a mutated class;
- a `files` entry has fewer Killed, Survived or NoCoverage mutants than its `killedAtLeast`, `survivedAtLeast` or `noCoverageAtLeast`. Its `path` is the file's path in the repo; it matches the report entry it ends with (`<package dir>/<file>`).

`risks` and `note` are for people only.

## Self-test

`selftest/run.sh` turns `selftest/fixture` (a Kotlin JVM build with a `:lib` module) into a git repo with this repository's wrapper and runs `selftest/corpus.json` against it in a temporary cache: `fixture-pass` must pass, `fixture-fail` (every expectation wrong) must fail, and running `fixture-pass` alone must pass.
