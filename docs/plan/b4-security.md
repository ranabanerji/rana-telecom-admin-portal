# B4. Production security

- **Track:** B (production migration)
- **Branch:** `plan/b4-security`
- **Depends on:** B2 and B3.

## Goal

Meet the security baseline for an internal production system holding customer data.

## Tasks

1. Authorization on every service method (`@PreAuthorize`), in addition to the URL rules. Add a
   role-matrix test that calls every endpoint as ADMIN, AGENT and no role.
2. An append-only `audit_events` table recording actor, action, entity, before and after values,
   request ID and time for every write. Admins can read it; nobody can change it through the app.
3. Secrets only from a secrets manager or platform secrets, with a rotation runbook. No secret
   defaults in YAML.
4. TLS for all traffic, including to the database and Kafka. HSTS on the frontend.
5. Rate limiting per user and per IP on writes and on the ingest API (Bucket4j, shared state if
   several replicas).
6. OWASP ZAP baseline scan in CI against the end-to-end stack.
7. An external penetration test before go-live.
8. Rewrite `SECURITY.md` for production: supported versions, reporting, and data handling.

## Done when

- The role-matrix test covers every endpoint.
- Every write appears in the audit log.
- ZAP and the penetration test report no high or critical findings.
