# Hardened demo and production migration plan

Telecom Demo stays a local demonstration application (see `SECURITY.md`). This plan has two tracks:

- **Track A, hardened demo (do now):** fixes that keep the demo's shape (SQLite, the two demo
  accounts, local only) and make a later production move cheaper.
- **Track B, production migration (recommendations):** a staged path to internal production, to
  start only when real users or real data are planned.

Each item has its own branch with a scope file under `docs/plan/`. Item branches start from
`plan/roadmap`, which holds this document.

| Item | Branch                    | Scope file                        |
| ---- | ------------------------- | --------------------------------- |
| A1   | `plan/a1-correctness`     | `docs/plan/a1-correctness.md`     |
| A2   | `plan/a2-performance`     | `docs/plan/a2-performance.md`     |
| A3   | `plan/a3-security`        | `docs/plan/a3-security.md`        |
| A4   | `plan/a4-supply-chain`    | `docs/plan/a4-supply-chain.md`    |
| A5   | `plan/a5-seams`           | `docs/plan/a5-seams.md`           |
| B0   | `plan/b0-readiness`       | `docs/plan/b0-readiness.md`       |
| B1   | `plan/b1-database`        | `docs/plan/b1-database.md`        |
| B2   | `plan/b2-identity`        | `docs/plan/b2-identity.md`        |
| B3   | `plan/b3-backend-scaling` | `docs/plan/b3-backend-scaling.md` |
| B4   | `plan/b4-security`        | `docs/plan/b4-security.md`        |
| B5   | `plan/b5-frontend`        | `docs/plan/b5-frontend.md`        |
| B6   | `plan/b6-integrations`    | `docs/plan/b6-integrations.md`    |
| B7   | `plan/b7-operations`      | `docs/plan/b7-operations.md`      |

## Current architecture

- **Frontend:** Next.js 16 and React 19. All pages are client components that call `/api/*`, which
  Next rewrites to the backend. Data fetching uses hand-written hooks; the login check runs in the
  browser.
- **Backend:** Spring Boot 4 on Java 21, Spring Data JPA (Hibernate 7) and Flyway. One SQLite file
  holds about 220k usage rows. Session-cookie login with SPA CSRF protection; two in-memory demo
  accounts.
- **Integrations:** Python seed and legacy-migration scripts and GitHub Actions CI. No external
  systems.

## Findings

| Area        | Finding                                                                                                                                                                                                                                  |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reliability | Concurrent dashboard requests fail. Under 1,200 parallel `revenue-by-plan` requests, `main` returned 373 errors (500) and the dependency-upgrade branch 76. Hibernate passes one shared UTC `Calendar` to sqlite-jdbc, which mutates it. |
| Performance | `stats` and `revenue-by-plan` load every active customer into memory and sum in Java. Every `@ManyToOne` is eager. Customer and device lists return up to 1,000 rows with no paging or search. `open-in-view` is on.                     |
| Scalability | SQLite allows one writer at a time; WAL and a busy timeout are not configured. Sessions are in memory, so only one backend instance can run. Nothing is cached.                                                                          |
| Security    | No login rate limit or lockout. Swagger and API docs are public. Next sets no security headers. Session timeout and cookie flags use defaults. Conflict detection depends on driver error text. No audit log.                            |
| Latency     | No response compression. Every request logs at INFO. Pages render only after client JavaScript loads.                                                                                                                                    |
| Operations  | No metrics, tracing or readiness checks. No container images. CI has no CodeQL, secret scanning or Dependabot configuration.                                                                                                             |

## Track A: hardened demo (about 2 to 3 weeks)

### A1. Correctness and reliability

1. Fix the dashboard concurrency failure: remove `hibernate.jdbc.time_zone: UTC` and read
   timestamps through a converter with no shared state. Add a parallel-request regression test.
2. Tune SQLite: WAL mode, `busy_timeout=5000`, a small pool with a single writer, and a startup
   check that the pragmas are set.

### A2. Performance (demo scale)

1. Compute `stats` and `revenue-by-plan` with SQL aggregates.
2. Lazy relationships with fetch joins where lists need them; `spring.jpa.open-in-view: false`.
3. Response compression, virtual threads, per-request logs at DEBUG.
4. Caffeine cache for the plan list (60 s, cleared on writes).

### A3. Demo-appropriate security

1. Login rate limit (about 5 attempts per minute per IP and username, 429).
2. 30-minute idle session timeout, configurable `Secure` cookie flag, session ID rotation test.
3. Security headers in `next.config.ts`: CSP, `X-Content-Type-Options`, `Referrer-Policy`,
   `frame-ancestors 'none'`.
4. A test that no error response includes SQL or driver text.
5. Swagger and API docs only in a `dev` profile or behind login.

### A4. Supply chain and CI

1. `.github/dependabot.yml` for npm, Gradle, pip and GitHub Actions (weekly, grouped).
2. CodeQL (Java, TypeScript) and gitleaks.
3. Gradle dependency locking and dependency-graph submission.
4. Merge `ranabanerji/dependabot-security-fixes`.

### A5. Seams for a later production move

1. Replace SQLite-only SQL (`strftime`, `unixepoch`) in `UsageRecordRepository.sumByTypeAndDate` and
   `CustomerRepository.countByMonth`.
2. Move the SQLite constraint-code handling in `ApiExceptionHandler` behind one adapter.
3. Actuator health (liveness, readiness) and a Micrometer metrics endpoint.
4. Every setting overridable from the environment, listed in `.env.example`.

**Track A is done when:** zero 500 errors under the parallel test; dashboard p95 under 100 ms on
seeded data; login returns 429 past the limit; security headers present; CI green with CodeQL and
gitleaks; all existing tests pass (79 backend, 64 frontend, 36 end-to-end).

## Track B: production migration (recommendations)

Sized for internal production at medium scale: up to 500 staff users, 1M customers and about 100M
usage rows per month (design for 400 events/s at peak, test at 1,000). Hosting is not decided, so
everything targets portable containers with environment-based configuration.

| Stage                   | Recommendation                                                                                                                                                                       | Why                                                                        | Effort    |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- | --------- |
| B0. Readiness gate      | Decide hosting, data classification, user and role ownership, and service targets (for example 99.9% availability, p95 under 200 ms).                                                | These choices set everything below.                                        | 1 week    |
| B1. Database            | PostgreSQL (managed where available): `timestamptz`, monthly usage partitions, dashboard rollups, Testcontainers in CI, one-time SQLite migration with dry run and row-count checks. | SQLite has one writer and one machine; it cannot hold 100M rows per month. | 2–3 weeks |
| B2. Identity            | Google Workspace OIDC handled by the backend, `hd` domain restriction, roles in an app table with an admin screen, Keycloak stand-in for local dev and CI, demo accounts removed.    | Staff already have Google accounts; tokens stay off the browser.           | 2 weeks   |
| B3. Backend scaling     | Spring Session JDBC, server-side search and paging, cursor paging for usage, ETags, `/api/v1`.                                                                                       | Any replica serves any user; deep pages stay fast.                         | 2 weeks   |
| B4. Production security | Per-method authorization, append-only audit log, secrets manager, TLS everywhere, OWASP ZAP in CI, penetration test before go-live.                                                  | Baseline for internal production.                                          | 2 weeks   |
| B5. Frontend            | Server-side login check (`proxy.ts`), TanStack Query, virtualized tables, nonce-based CSP, standalone build.                                                                         | Faster first load, fewer duplicate requests, stronger XSS protection.      | 2 weeks   |
| B6. Integrations        | Kafka with Schema Registry: idempotent usage ingest with a dead-letter topic; transactional outbox for billing, CRM, HMAC-signed webhooks and notifications.                         | Absorbs usage bursts without losing or duplicating events.                 | 3–4 weeks |
| B7. Operations          | OpenTelemetry, Prometheus and Grafana with alerts; signed (cosign), scanned (Trivy) images with SBOMs; runbooks for backup and restore, partitions, secret rotation and rollback.    | You can see, roll back and recover from failures.                          | 1–2 weeks |

**Order:** B0 → B1 → (B2, B3 and B5 in parallel) → B4 → (B6 and B7). About 14 to 18 weeks for one
or two engineers; Track A's seams shorten B1 and B3.

### Go-live checklist

- k6 on medium-scale synthetic data: p95 under 200 ms for lists and the dashboard, error rate below
  0.1%.
- Kafka ingest holds 1,000 events/s for 30 minutes with lag under 30 s and zero duplicates.
- No high or critical findings from ZAP or the penetration test; no open high CodeQL or Dependabot
  alerts.
- Backup restore tested; recovery targets agreed (for example 15 minutes of data loss, 1 hour to
  recover).
- Role matrix tested for every endpoint; audit log covers every write.
- `SECURITY.md` rewritten for production use.

### Open decisions

- Hosting platform (Kubernetes, managed containers or VMs).
- Billing and CRM vendors, email provider and Slack workspace.
- Retention period for raw usage (for example 13 months, older data archived to object storage).
