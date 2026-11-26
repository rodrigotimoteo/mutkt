#!/usr/bin/env bash
# Runs published.sh against a local server: /ok/* answers 200, /redirect/* answers 303 to /ok/* and /cdn/* 303 to
# localhost/ok/* (a second host standing in for the Portal CDN), anything else 404.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'kill "$server"; wait "$server" 2>/dev/null || true; rm -rf "$work"' EXIT

python3 -u - >"$work/port" <<'EOF' &
import http.server

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/redirect/"):
            self.send_response(303)
            self.send_header("Location", "/ok/" + self.path[len("/redirect/"):])
        elif self.path.startswith("/cdn/"):
            self.send_response(303)
            self.send_header("Location", f"http://localhost:{self.server.server_port}/ok/" + self.path[len("/cdn/"):])
        else:
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
port=$(cat "$work/port")
url="http://127.0.0.1:$port"
export PLUGIN_PORTAL_ARTIFACTS_HOST="localhost:$port"

expect() {
  local want=$1 got=0
  shift
  env "$@" >/dev/null 2>&1 || got=$?
  [[ $got == "$want" ]] || { echo "FAIL: $* exited $got, expected $want" >&2; exit 1; }
}

expect 0 PLUGIN_PORTAL_URL="$url/ok" "$here/published.sh" plugin-portal 1.0.0
expect 1 PLUGIN_PORTAL_URL="$url/redirect" "$here/published.sh" plugin-portal 1.0.0
expect 0 PLUGIN_PORTAL_URL="$url/cdn" "$here/published.sh" plugin-portal 1.0.0
expect 1 PLUGIN_PORTAL_URL="$url/missing" "$here/published.sh" plugin-portal 1.0.0
expect 0 CENTRAL_URL="$url/redirect" "$here/published.sh" central 1.0.0
expect 1 CENTRAL_URL="$url/missing" "$here/published.sh" central 1.0.0
echo "published.sh checks passed"
