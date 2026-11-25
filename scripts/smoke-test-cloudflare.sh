#!/usr/bin/env bash
# Runs the Cloudflare build (Worker, static assets and backend container) locally with `wrangler dev`
# and checks pages, the API route and a login through the Worker. Needs Docker, a prior
# `pnpm build:cloudflare`, and DEMO_ADMIN_PASSWORD / DEMO_AGENT_PASSWORD in the environment.
set -euo pipefail

: "${DEMO_ADMIN_PASSWORD:?Set DEMO_ADMIN_PASSWORD}"
: "${DEMO_AGENT_PASSWORD:?Set DEMO_AGENT_PASSWORD}"

cd "$(dirname "$0")/.."
port="${SMOKE_PORT:-8787}"
base="http://127.0.0.1:$port"
work="$(mktemp -d)"
log="$work/wrangler.log"

if [[ -e .dev.vars ]]; then
  echo "Refusing to overwrite an existing .dev.vars" >&2
  exit 1
fi
printf 'DEMO_ADMIN_PASSWORD=%s\nDEMO_AGENT_PASSWORD=%s\n' "$DEMO_ADMIN_PASSWORD" "$DEMO_AGENT_PASSWORD" > .dev.vars

pnpm exec wrangler dev --port "$port" --ip 127.0.0.1 --show-interactive-dev-session=false > "$log" 2>&1 &
wrangler_pid=$!
cleanup() {
  kill "$wrangler_pid" 2> /dev/null || true
  wait "$wrangler_pid" 2> /dev/null || true
  rm -f .dev.vars
  echo "--- wrangler dev log ---"
  cat "$log"
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# The first start builds the container image, which takes a few minutes.
for _ in $(seq 1 600); do
  curl -sf -o /dev/null "$base/" && break
  kill -0 "$wrangler_pid" 2> /dev/null || fail "wrangler dev exited"
  sleep 1
done

check_status() {
  local path="$1" expected="$2" status
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 300 "$base$path")"
  [[ "$status" == "$expected" ]] || fail "GET $path returned $status, expected $expected"
  echo "ok: GET $path -> $status"
}

check_status / 200
check_status /login 200
check_status /customers 200
check_status /no-such-page 404
# Starts the backend container on first use.
check_status /api/health 200
check_status /api/auth/me 401

jar="$work/cookies"
curl -sf -c "$jar" -b "$jar" -o /dev/null "$base/api/auth/csrf" || fail "GET /api/auth/csrf"
token="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")"
[[ -n "$token" ]] || fail "no XSRF-TOKEN cookie"
status="$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{http_code}' -H "X-XSRF-TOKEN: $token" \
  -d username=admin --data-urlencode "password=$DEMO_ADMIN_PASSWORD" "$base/api/auth/login")"
[[ "$status" == 200 ]] || fail "admin login returned $status"
echo "ok: admin login through the Worker"

customer="$(curl -sf -b "$jar" "$base/api/customers/1")" || fail "GET /api/customers/1"
[[ "$customer" == *'"id":1'* ]] || fail "seeded customer 1 missing: $customer"
echo "ok: seeded data served through the Worker"
