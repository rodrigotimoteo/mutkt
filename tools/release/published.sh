#!/usr/bin/env bash
# Exits 0 when every artifact of a version is already in the target repository, so a retry skips finished stages.
# Usage: tools/release/published.sh <central|plugin-portal> <version> [wait-seconds]
# CENTRAL_URL, PLUGIN_PORTAL_URL and PLUGIN_PORTAL_ARTIFACTS_HOST point a rehearsal at a disposable repository.
set -euo pipefail

target=${1:?usage: published.sh <central|plugin-portal> <version> [wait-seconds]}
version=${2:?usage: published.sh <central|plugin-portal> <version> [wait-seconds]}
wait_seconds=${3:-0}
group_path=io/github/rodrigotimoteo
marker_id=io.github.rodrigotimoteo.mutkt.gradle.plugin
marker="$group_path/mutkt/$marker_id/$version/$marker_id-$version.pom"

case "$target" in
  central)
    base=${CENTRAL_URL:-https://repo1.maven.org/maven2}
    follow=--location
    paths=("$marker")
    for module in mutkt-annotations mutkt-core mutkt-asm mutkt-junit mutkt-report mutkt-gradle; do
      paths+=("$group_path/$module/$version/$module-$version.pom")
    done
    ;;
  plugin-portal)
    base=${PLUGIN_PORTAL_URL:-https://plugins.gradle.org/m2}
    # The Portal answers 303 to Central for artifacts it does not host and 303 to its CDN for some it does.
    follow=--no-location
    artifacts_host=${PLUGIN_PORTAL_ARTIFACTS_HOST:-plugins-artifacts.gradle.org}
    paths=("$marker")
    ;;
  *) echo "unknown target: $target" >&2; exit 2 ;;
esac

present() {
  local code url
  read -r code url < <(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$follow" "$1")
  [[ $code == 200 ]] || [[ $target == plugin-portal && $code == 3* && $url == http*://"$artifacts_host"/* ]]
}

missing() {
  local path
  for path in "${paths[@]}"; do
    present "$base/$path" || { echo "$base/$path"; return 0; }
  done
  return 1
}

deadline=$((SECONDS + wait_seconds))
while first=$(missing); do
  if ((SECONDS >= deadline)); then
    echo "$target does not have $version yet, first missing: $first" >&2
    exit 1
  fi
  sleep 30
done
echo "$target has all artifacts of $version"
