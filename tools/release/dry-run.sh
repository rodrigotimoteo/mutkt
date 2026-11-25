#!/usr/bin/env bash
# Publishes to a throwaway local Maven repository, signed with a throwaway key, then checks the result.
# Usage: tools/release/dry-run.sh <version> [extra gradle arguments]
set -euo pipefail

version=${1:?usage: dry-run.sh <version> [gradle arguments]}
shift
root=$(cd "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Without a key every module publishes unsigned, the plugin marker included.
env -u ORG_GRADLE_PROJECT_signingInMemoryKey "$root/gradlew" -p "$root" publishToMavenLocal "-Pmutkt.version=$version" \
  "-Dmaven.repo.local=$work/m2-unsigned" "$@"
marker_id=io.github.rodrigotimoteo.mutkt.gradle.plugin
[[ -f "$work/m2-unsigned/io/github/rodrigotimoteo/mutkt/$marker_id/$version/$marker_id-$version.pom" ]] ||
  { echo "unsigned publication: no plugin marker" >&2; exit 1; }
if find "$work/m2-unsigned" -name '*.asc' | grep -q .; then echo "unsigned publication: found signatures" >&2; exit 1; fi
echo "unsigned publication verified"

export GNUPGHOME="$work/gnupg"
mkdir -m 700 "$GNUPGHOME"
gpg --batch --quiet --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'MutKt dry run <dry-run@example.invalid>' rsa3072 sign never
ORG_GRADLE_PROJECT_signingInMemoryKey=$(gpg --batch --pinentry-mode loopback --passphrase '' --armor --export-secret-keys)
export ORG_GRADLE_PROJECT_signingInMemoryKey

"$root/gradlew" -p "$root" publishToMavenLocal "-Pmutkt.version=$version" "-Dmaven.repo.local=$work/m2" "$@"
"$root/tools/release/verify-publication.sh" "$work/m2" "$version"
