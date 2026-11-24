# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-24

### Security

- The backend and the frontend now listen on `127.0.0.1` by default, so other machines on
  the network can no longer reach the unauthenticated API or the admin UI (FAC-11). This
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

- The backend CI build no longer fails on 33 PMD violations in the controller tests. PMD
  now treats MockMvc `andExpect(...)` as an assertion, and the tests were tidied up.

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

[0.2.0]: https://github.com/ranabanerji/rana-telecom-admin-portal/compare/660d69d...v0.2.0
[0.1.0]: https://github.com/ranabanerji/rana-telecom-admin-portal/tree/660d69d
