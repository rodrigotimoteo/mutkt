#!/usr/bin/env bash
# Runs notes.sh against a small CHANGELOG: sections found exactly, trimmed, missing or empty ones rejected.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export CHANGELOG="$work/CHANGELOG.md"
cat >"$CHANGELOG" <<'EOF'
# Changelog

## [Unreleased]

## [1.1.0] - 2026-02-01

Second.

### Fixed

- A fix.

## [1.1.0-rc.1] - 2026-01-15

Candidate.
## [1.0.0]
First.
EOF

expect() {
  local version=$1 want=$2 got
  got=$("$here/notes.sh" "$version") || { echo "FAIL: notes.sh $version failed" >&2; exit 1; }
  [[ "$got" == "$want" ]] || { printf 'FAIL: notes.sh %s printed\n%s\n' "$version" "$got" >&2; exit 1; }
}

fails() {
  if "$here/notes.sh" "$1" >/dev/null 2>&1; then echo "FAIL: notes.sh $1 succeeded" >&2; exit 1; fi
}

expect 1.1.0 $'Second.\n\n### Fixed\n\n- A fix.'
expect 1.1.0-rc.1 'Candidate.'
expect 1.0.0 'First.'
fails Unreleased
fails 1.1
fails 2.0.0
echo "notes.sh checks passed"
