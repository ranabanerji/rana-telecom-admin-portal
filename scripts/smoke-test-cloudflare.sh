#!/usr/bin/env bash
# Runs the Cloudflare build (Worker, static assets and backend container) locally with `wrangler dev`
# and checks pages, headers, the Access check on the API route and a login through the Worker. Needs Docker, a prior
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
# wrangler dev serves plain HTTP, where browsers and curl drop Secure cookies.
printf 'DEMO_ADMIN_PASSWORD=%s\nDEMO_AGENT_PASSWORD=%s\nSESSION_COOKIE_SECURE=false\n' \
  "$DEMO_ADMIN_PASSWORD" "$DEMO_AGENT_PASSWORD" > .dev.vars
# The Worker refuses /api/* without a valid Access token; sign one with a throwaway key.
access_token="$(node scripts/dev-access-token.mjs .dev.vars)" || {
  rm -f .dev.vars
  exit 1
}
access=(-H "Cf-Access-Jwt-Assertion: $access_token")

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

# Usage: check_status PATH EXPECTED_STATUS [CURL_ARGS...]
check_status() {
  local path="$1" expected="$2" status
  shift 2
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 300 "$@" "$base$path")"
  [[ "$status" == "$expected" ]] || fail "GET $path returned $status, expected $expected"
  echo "ok: GET $path -> $status"
}

check_header() {
  local path="$1" name="$2" headers
  shift 2
  headers="$(curl -s -o /dev/null -D - --max-time 300 "$@" "$base$path")"
  grep -qi "^$name:" <<< "$headers" || fail "GET $path has no $name header"
  echo "ok: GET $path sends $name"
}

check_status / 200
check_status /login 200
check_status /customers 200
check_status /no-such-page 404
check_header /login Content-Security-Policy
check_header /login Strict-Transport-Security
check_status /api/health 403
check_status /api/health 403 -H "Cf-Access-Jwt-Assertion: not-a-token"
# Starts the backend container on first use.
check_status /api/health 200 "${access[@]}"
check_header /api/health Strict-Transport-Security "${access[@]}"
check_status /api/auth/me 401 "${access[@]}"

jar="$work/cookies"
curl -sf "${access[@]}" -c "$jar" -b "$jar" -o /dev/null "$base/api/auth/csrf" || fail "GET /api/auth/csrf"
token="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")"
[[ -n "$token" ]] || fail "no XSRF-TOKEN cookie"
status="$(curl -s "${access[@]}" -c "$jar" -b "$jar" -o /dev/null -w '%{http_code}' -H "X-XSRF-TOKEN: $token" \
  -d username=admin --data-urlencode "password=$DEMO_ADMIN_PASSWORD" "$base/api/auth/login")"
[[ "$status" == 200 ]] || fail "admin login returned $status"
echo "ok: admin login through the Worker"

customer="$(curl -sf "${access[@]}" -b "$jar" "$base/api/customers/1")" || fail "GET /api/customers/1"
[[ "$customer" == *'"id":1'* ]] || fail "seeded customer 1 missing: $customer"
echo "ok: seeded data served through the Worker"
