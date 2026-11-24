# A3. Demo-appropriate security

- **Track:** A (hardened demo)
- **Branch:** `plan/a3-security`
- **Depends on:** nothing.

## Goal

Close the obvious gaps that matter even for a local demo, without changing how login works.

## Evidence

- `SecurityConfig` has no limit on `POST /api/auth/login` attempts.
- `/swagger-ui/**` and `/v3/api-docs/**` are `permitAll`.
- `next.config.ts` defines rewrites only, with no `headers()`.
- `application.yaml` sets `http-only` and `same-site: lax` but no session timeout or `secure` flag.
- `ApiExceptionHandler.integrity` and `sqliteIntegrity` pass the database message to `conflict()`.
  Responses don't include it today (it only picks the field), but no test guards that.

## Tasks

1. Rate-limit login with Bucket4j or a small in-memory limiter: about 5 failures per minute per IP
   and per username, returning 429 with `Retry-After`.
2. Set `server.servlet.session.timeout: 30m` and make `cookie.secure` configurable
   (`TELECOM_COOKIE_SECURE`, default `false` for local HTTP).
3. Add a test that the session ID changes after login (session-fixation protection).
4. Add `headers()` in `next.config.ts`: a Content-Security-Policy suited to the app (`default-src
'self'`, no inline scripts except what Next needs), `X-Content-Type-Options: nosniff`,
   `Referrer-Policy: strict-origin-when-cross-origin`, `frame-ancestors 'none'`.
5. Add a test that triggers each conflict (duplicate email, IMEI and SIM, and dependent deletes)
   and asserts that no response includes SQL or driver text. Log the cause at DEBUG.
6. Enable Swagger and API docs only when a `dev` profile is active, or require login for them.

## Done when

- The sixth failed login within a minute returns 429.
- Response headers are checked in an end-to-end test.
- No API response contains SQL or driver text (checked by a test that triggers each conflict).
- `/swagger-ui.html` is unavailable without the `dev` profile.

## Out of scope

- Real identity, MFA and audit logging (see B2 and B4).
