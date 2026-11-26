#!/usr/bin/env bash
# Runs the Plugin Portal upload command, then waits until the Portal serves the plugin marker.
# The first upload of a new plugin id waits for manual approval by Gradle, so the marker cannot appear in time:
# when the upload output says so, the wait is skipped with a warning and the release goes on.
# Usage: tools/release/publish-plugin.sh <version> <wait-seconds> <upload command...>
set -euo pipefail

version=${1:?usage: publish-plugin.sh <version> <wait-seconds> <upload command...>}
wait_seconds=${2:?usage: publish-plugin.sh <version> <wait-seconds> <upload command...>}
shift 2
(($#)) || { echo "usage: publish-plugin.sh <version> <wait-seconds> <upload command...>" >&2; exit 2; }
here=$(cd "$(dirname "$0")" && pwd)
log=$(mktemp)
trap 'rm -f "$log"' EXIT

"$@" | tee "$log"
if grep -qi "submitted for approval" "$log"; then
  echo "::warning::The Plugin Portal holds $version for approval by Gradle; not waiting for it to be served"
  exit 0
fi
"$here/published.sh" plugin-portal "$version" "$wait_seconds"
