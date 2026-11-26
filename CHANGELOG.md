# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-11-25

### Added

- A backend service layer (FAC-39): `CustomerService`, `PlanService`, `DeviceService` and
  `TicketService` hold the business rules and run each request in a transaction (read-only for
  reads). The customer, plan, device and ticket controllers only map requests and responses.
- `DashboardService` (FAC-31): every dashboard number comes from a COUNT/GROUP BY query instead
  of loading entity lists into memory. Plan aggregates are keyed by plan ID, so duplicate plan
  names stay separate, and revenue is price times count in `BigDecimal` (SQLite's SUM returns a
  REAL, which would round two-decimal prices).
- Developer setup (FAC-35): `.tool-versions` pins Node 22, Java 21, Python 3.12 and pnpm 10.33.4
  for mise, `pnpm bootstrap` installs and seeds in one step, `pnpm run doctor` checks the
  toolchain and prints fixes, and the README has a three-command quick start.

### Changed

- The device assignment rules (FAC-17) and the ticket status rules that set or clear
  `resolved_at` (FAC-20) now live in `DeviceService` and `TicketService`, with unit tests
  for every status change.
- A request for a customer, plan, device or ticket that does not exist now returns a 404
  problem-details body (with `errors.id`) instead of an empty 404.
- Server-set timestamps (`assigned_at`, `resolved_at`) are millisecond precision, matching
  what the database stores.

### Removed

- Four repository methods nothing called (FAC-26): `UsageRecordRepository.sumByTypeAndDate`,
  `CustomerRepository.countByMonth`, `DeviceRepository.findByStatus` and
  `SupportTicketRepository.findByStatus`. `CustomerRepository.findByStatus` also went with the
  dashboard's move to aggregate queries (FAC-31).

## [0.2.0] - 2026-11-24

### Security

- The backend and the frontend now listen on `127.0.0.1` by default, so other machines on
  the network can no longer reach the API or the admin UI (FAC-11). This
  applies to `pnpm dev` and `pnpm start`.
- Swagger UI (`/swagger-ui.html`) and the OpenAPI docs (`/v3/api-docs`) are off by default.
  They are on only in the `dev` Spring profile, which `pnpm dev:backend` (Gradle `bootRun`)
  selects unless `SPRING_PROFILES_ACTIVE` is set.

### Added

- `SERVER_ADDRESS` environment variable to choose the backend bind address, and `-H` on the
  frontend scripts, for when you need to accept connections from other machines on
  purpose.
- "Network Access" section in `README.md` and a `SECURITY.md` explaining the exposure
  risks, containers, and tunnels.
- Tests that check the loopback defaults, the `dev` profile, and the `SERVER_ADDRESS`
  override (`NetworkExposure*Test`, `__tests__/listen-address.test.ts`).

### Changed

- Local and CI URLs (`next.config.ts`, `playwright.config.ts`, e2e specs, CI env) use
  `127.0.0.1` instead of `localhost`.

### Fixed

- The backend CI build no longer fails on 33 PMD violations in the test code (FAC-10). Test
  sources use a separate PMD rule set that treats MockMvc `andExpect(...)` as an assertion.

## [0.1.0] - 2026-09-16

### Added

- Initial telecom demo admin portal: a Next.js 16 frontend and a Spring Boot 4 REST API
  backed by SQLite.
- Admin screens for the dashboard, customers, plans, devices, usage records, and support
  tickets.
- Python seeder (`scripts/seed-database.py`) that generates deterministic demo data.
- Frontend (Vitest), backend (JUnit), and end-to-end (Playwright) tests, plus a GitHub
  Actions CI workflow.
- License that lets interview candidates fork and modify the project.

[0.3.0]: https://github.com/ranabanerji/rana-telecom-admin-portal/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/ranabanerji/rana-telecom-admin-portal/compare/660d69d...v0.2.0
[0.1.0]: https://github.com/ranabanerji/rana-telecom-admin-portal/tree/660d69d
