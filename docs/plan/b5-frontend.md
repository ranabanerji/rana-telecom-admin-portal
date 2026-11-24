# B5. Frontend

- **Track:** B (production migration)
- **Branch:** `plan/b5-frontend`
- **Depends on:** B3 (server-side paging API). Can run alongside B2.

## Goal

A faster, safer frontend that works with server-side paging and the new identity flow.

## Tasks

1. Redirect users without a session before the page loads, using Next 16 `proxy.ts`. Keep the
   browser-side check as a fallback.
2. Replace the hand-written data hooks with TanStack Query: caching, request deduplication,
   retries with backoff, refetch on focus and optimistic updates. Keep the stale-response
   protections from FAC-16.
3. Rebuild tables for server-side paging: search, sort and filters stored in the URL, row
   virtualization and infinite scroll for usage.
4. Render the page shell on the server, load charts lazily and set a bundle-size budget in CI.
   Add an error boundary per section.
5. Use a nonce-based Content-Security-Policy and add HSTS.
6. Build with `output: "standalone"` for a small container image.
7. Add screens for role management (B2), the audit log (B4) and ingest status (B6).

## Done when

- Lighthouse performance score of 90 or more on the dashboard.
- First useful content in under 1.5 s on a warm cache.
- No CSP violations during end-to-end tests.
- The Playwright suite passes, plus new tests for search, paging and roles.
