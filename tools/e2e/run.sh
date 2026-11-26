#!/usr/bin/env bash
# Runs MutKt, built from this working tree, on the pinned repos of a corpus and checks each report against its
# expectations. Local only (see tools/e2e/README.md).
# Usage: tools/e2e/run.sh [--corpus <corpus.json>] [repo name]
set -euo pipefail

e2e=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$e2e/../.." && pwd)
corpus=$e2e/corpus.json
if [[ ${1:-} == --corpus ]]; then
  corpus=$(cd "$(dirname "${2:?--corpus needs a file}")" && pwd)/$(basename "$2")
  shift 2
fi
only=${1:-}
cache=${MUTKT_E2E_CACHE:-$HOME/.cache/mutkt-e2e}
local_repo=$root/build/e2e-repo
json() { python3 -I "$e2e/e2e.py" "$@"; }

[[ -f $corpus ]] || { echo "no corpus at $corpus" >&2; exit 1; }
# Fails before any clone when the corpus or the repo name is wrong.
fields=$(json fields "$corpus" ${only:+"$only"})
mkdir -p "$cache/repos" "$cache/logs"

echo "Publishing MutKt 0.0.0-e2e to $local_repo"
env -u ORG_GRADLE_PROJECT_signingInMemoryKey "$root/gradlew" -p "$root" -q publishAllPublicationsToCentralRepository \
  -Pmutkt.version=0.0.0-e2e "-Pmutkt.publishUrl=file://$local_repo"

# Checks out the pinned commit into a clean tree, keeping build outputs so reruns stay incremental.
checkout() {
  local dir=$1 url=$2 commit=$3
  [[ -d $dir/.git ]] || git init -q "$dir"
  git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null || git -C "$dir" fetch -q --depth 1 "$url" "$commit"
  git -C "$dir" checkout -q --force --detach "$commit"
  git -C "$dir" clean -qfd
}

# Prints one result line: PASS or FAIL, then the details. Gradle's output goes to the log.
run_repo() {
  local name=$1 url=$2 commit=$3 module=$4 jdk=$5 apply=$6 patch=$7
  local dir=$cache/repos/$name log=$cache/logs/$name.log
  local expect=$(dirname "$corpus")/expectations/$name.json
  [[ -f $expect ]] || { echo "FAIL no expectations file $expect"; return; }
  checkout "$dir" "$url" "$commit" >"$log" 2>&1 || { echo "FAIL checkout failed (log: $log)"; return; }
  if [[ $apply == patch ]]; then
    git -C "$dir" apply "$(dirname "$corpus")/$patch" >>"$log" 2>&1 || { echo "FAIL patch $patch does not apply"; return; }
  fi
  json entry "$corpus" "$name" >"$cache/logs/$name.entry.json"
  local task=${module%:}:mutkt module_dir=${module#:}
  local java_home gradle_args=() arg args_text
  args_text=$(json gradle-args "$corpus" "$name" 2>&1) || { echo "FAIL $args_text"; return; }
  [[ -z $args_text ]] || while IFS= read -r arg; do gradle_args+=("$arg"); done <<<"$args_text"
  java_home=$(/usr/libexec/java_home -v "$jdk" 2>/dev/null) || { echo "FAIL no JDK $jdk installed"; return; }
  # --no-scan is the only switch that beats a build's own publishing.onlyIf { true }: Develocity ORs every onlyIf.
  local built=true
  JAVA_HOME=$java_home "$dir/gradlew" -p "$dir" --no-scan --init-script "$e2e/mutkt-e2e.init.gradle" \
    "-Dmutkt.e2e.repo=file://$local_repo" "-Dmutkt.e2e.entry=$cache/logs/$name.entry.json" \
    ${gradle_args[@]+"${gradle_args[@]}"} "$task" >>"$log" 2>&1 || built=false
  # Scans carry the user name, host name and paths: one that got out anyway must not go unnoticed.
  if grep -Eq 'Publishing build scan|gradle\.com/s/' "$log"; then
    echo "FAIL build scan published: delete it at the URL in the log (log: $log)"
    return
  fi
  if [[ $built == false ]]; then
    echo "FAIL $task failed (log: $log)"
    grep -q 'MutKt cannot load the Android Gradle plugin' "$log" && echo "  AGP is not visible to MutKt: use patch mode"
    return
  fi
  local report=$dir/${module_dir//://}/build/reports/mutkt/mutation-report.json
  local checked
  if checked=$(json check "$expect" "$report" 2>&1); then echo "PASS $checked"; else echo "FAIL $checked"; fi
}

results=()
while IFS=$'\t' read -r name url commit module jdk apply patch; do
  echo "Running $name ($module at ${commit:0:12})"
  # A repo that fails, even with a non-zero status, is a FAIL row: the loop goes on to the next repo.
  if ! outcome=$(run_repo "$name" "$url" "$commit" "$module" "$jdk" "$apply" "$patch"); then
    [[ $outcome == FAIL* ]] || outcome="FAIL runner stopped: ${outcome:-no output}"
  fi
  results+=("$name"$'\t'"$outcome")
done <<<"$fields"

status=0
echo
printf '%-28s %-6s %s\n' REPO RESULT DETAIL
for result in "${results[@]}"; do
  name=${result%%$'\t'*}
  outcome=${result#*$'\t'}
  printf '%-28s %-6s %s\n' "$name" "${outcome%% *}" "${outcome#* }"
  [[ $outcome == PASS* ]] || status=1
done
exit "$status"
