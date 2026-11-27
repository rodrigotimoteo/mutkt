#!/usr/bin/env bash
# Self-test of the e2e runner on a local fixture: one repo whose build fails, one whose expectations hold, one whose
# expectations fail and one whose log shows a build scan upload.
# Usage: tools/e2e/selftest/run.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fail() { echo "selftest: $*" >&2; exit 1; }

# The fixture becomes a git repository with the root's Gradle wrapper, like a real corpus repo.
cp -R "$here/fixture" "$work/fixture"
cp -R "$root/gradle" "$root/gradlew" "$work/fixture/"
rm -f "$work/fixture/gradle/libs.versions.toml"
git -C "$work/fixture" init -q
git -C "$work/fixture" add -A
git -C "$work/fixture" -c user.name=e2e -c user.email=e2e@example.invalid commit -q -m fixture
commit=$(git -C "$work/fixture" rev-parse HEAD)

mkdir "$work/corpus"
cp -R "$here/expectations" "$work/corpus/"
sed -e "s|@FIXTURE_URL@|$work/fixture|" -e "s|@FIXTURE_COMMIT@|$commit|" "$here/corpus.json" >"$work/corpus/corpus.json"
export MUTKT_E2E_CACHE="$work/cache"

if "$root/tools/e2e/run.sh" --corpus "$work/corpus/corpus.json" | tee "$work/all.txt"; then
  fail "the whole corpus passed, but fixture-fail must fail"
fi
grep -Eq '^fixture-broken +FAIL +:lib:mutkt failed ' "$work/all.txt" || fail "fixture-broken did not fail its build"
grep -Eq '^fixture-pass +PASS ' "$work/all.txt" || fail "fixture-pass did not pass"
grep -Eq '^fixture-fail +FAIL ' "$work/all.txt" || fail "fixture-fail did not fail"
grep -Eq '^fixture-leak +FAIL +build scan published: ' "$work/all.txt" || fail "fixture-leak did not fail on its scan"

"$root/tools/e2e/run.sh" --corpus "$work/corpus/corpus.json" fixture-pass | tee "$work/one.txt" ||
  fail "fixture-pass alone failed"
grep -q '^fixture-fail' "$work/one.txt" && fail "running fixture-pass alone also ran fixture-fail"
echo "selftest passed"
