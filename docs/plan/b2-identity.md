# B2. Identity: Google Workspace sign-in

- **Track:** B (production migration)
- **Branch:** `plan/b2-identity`
- **Depends on:** B1 (role and session tables live in PostgreSQL).

## Goal

Staff sign in with their Google Workspace accounts. The demo accounts go away.

## Recommendation

Spring Security `oauth2Login` with the backend as a confidential OIDC client (the "backend for
frontend" pattern). The browser keeps only the session cookie, so the current CSRF design stays.

## Tasks

1. Register an OAuth client in Google Cloud. Use the authorization code flow with PKCE and verify
   `state` and `nonce`.
2. Allow only your domain: check the `hd` claim and `email_verified`, and reject everything else.
3. Store roles in a `user_roles` table keyed by email (ADMIN, AGENT). Google doesn't put group
   membership in the ID token, so roles stay in the app. Syncing from Google groups through the
   Admin SDK is a later option.
4. Add an admin screen to grant and revoke roles. The first admin is bootstrapped from an
   environment variable.
5. Use Keycloak in Docker Compose as a stand-in identity provider for local development and
   Playwright.
6. Remove `DemoUserDirectory`, the demo password variables and the form-login endpoint.
7. Support logout and session expiry from the app (no Google global logout).

## Done when

- Only verified accounts in your domain can sign in.
- A user with no role sees an "access not granted" page and cannot call the API.
- Playwright login runs against Keycloak in CI.
