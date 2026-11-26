#!/usr/bin/env bash
# Runs publish-plugin.sh with stand-in upload commands against a local server: /ok/* answers 200, anything else 404.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'kill "$server"; wait "$server" 2>/dev/null || true; rm -rf "$work"' EXIT

python3 -u - >"$work/port" <<'EOF' &
import http.server

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200 if self.path.startswith("/ok/") else 404)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass

server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
print(server.server_port)
server.serve_forever()
EOF
server=$!
for _ in {1..50}; do [[ -s "$work/port" ]] && break; sleep 0.1; done
url="http://127.0.0.1:$(cat "$work/port")"

expect() {
  local want=$1 got=0
  shift
  env "$@" >"$work/out" 2>&1 || got=$?
  [[ $got == "$want" ]] || { echo "FAIL: $* exited $got, expected $want" >&2; cat "$work/out" >&2; exit 1; }
}

pending='Plugin io.github.rodrigotimoteo.mutkt 1.0.0 has been submitted for approval'
expect 0 PLUGIN_PORTAL_URL="$url/missing" "$here/publish-plugin.sh" 1.0.0 0 echo "$pending"
grep -q '^::warning::' "$work/out" || { echo "FAIL: no warning for a plugin held for approval" >&2; exit 1; }
expect 0 PLUGIN_PORTAL_URL="$url/ok" "$here/publish-plugin.sh" 1.0.0 0 echo published
expect 1 PLUGIN_PORTAL_URL="$url/missing" "$here/publish-plugin.sh" 1.0.0 0 echo published
expect 1 PLUGIN_PORTAL_URL="$url/ok" "$here/publish-plugin.sh" 1.0.0 0 sh -c "echo '$pending'; exit 1"
echo "publish-plugin.sh checks passed"
