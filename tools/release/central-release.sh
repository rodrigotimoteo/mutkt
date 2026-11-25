#!/usr/bin/env bash
# Hands the uploaded staging repository to the Central Portal, which validates it and publishes automatically.
# Needs CENTRAL_USERNAME and CENTRAL_PASSWORD (a Central Portal user token) in the environment.
set -euo pipefail

token=$(printf '%s:%s' "${CENTRAL_USERNAME:?}" "${CENTRAL_PASSWORD:?}" | base64 | tr -d '\n')
# The header goes through stdin so the token never appears in a process list.
printf 'header = "Authorization: Bearer %s"\n' "$token" |
  curl -fsS --config - -X POST \
    "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.rodrigotimoteo?publishing_type=automatic"
