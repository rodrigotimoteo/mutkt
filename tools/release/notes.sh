#!/usr/bin/env bash
# Prints the CHANGELOG section of a version (without its heading), the body of the GitHub release.
# Usage: tools/release/notes.sh <version>. CHANGELOG points it at another file (notes-test.sh).
set -euo pipefail

version=${1:?usage: notes.sh <version>}
changelog=${CHANGELOG:-$(cd "$(dirname "$0")/../.." && pwd)/CHANGELOG.md}

# Blank lines are held back until more text follows, so the body comes out trimmed at both ends.
notes=$(awk -v heading="## [$version]" '
  index($0, "## [") == 1 {
    if (found) exit
    rest = substr($0, length(heading) + 1)
    found = index($0, heading) == 1 && (rest == "" || rest ~ /^ /)
    next
  }
  found && /^[[:space:]]*$/ { if (started) blank = blank "\n"; next }
  found { printf "%s%s\n", blank, $0; blank = ""; started = 1 }
' "$changelog")

[[ -n "$notes" ]] || { echo "notes.sh: no notes for $version in $changelog" >&2; exit 1; }
printf '%s\n' "$notes"
