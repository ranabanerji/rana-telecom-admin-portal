# Selected issue implementation

Scope is the confirmed rana-telecom-admin-portal repository. Started from main; related issues are context only.

Dependency order:

1. FAC-10: separate test PMD correctness/security policy; retain production policy.
2. FAC-32, FAC-5, FAC-6, FAC-22: backend Flyway schema, enforced foreign keys/indexes, migrated SQLite tests and setup; UTC epoch milliseconds, exclusive dateTo, deterministic bounded seed data. Retained naive timestamps require an explicit source timezone and conversion into a separate database, with inventory and original preserved.
3. FAC-7, FAC-8, FAC-21: dependent-delete conflicts with visible errors; aggregate plans by ID.
4. FAC-9, FAC-12, FAC-17, FAC-20: field validation and Problem Details, optional descriptions, explicit device assignment and ticket resolution transitions.
5. FAC-15, FAC-16, FAC-18: independent dashboard failures, retryable race-safe pagination, stable bounded lists with visible totals.
6. Regression coverage for all selected issues, backend build/test/lint, frontend tests/lint/build, disposable setup/browser checks and UI evidence; push and draft PR.

Verification and the issue-to-test mapping are recorded below when complete. No live or retained database will be modified.

Acceptance coverage:

| Issue  | Fix and regression coverage                                                                                                                                                                                                                                                                                       |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FAC-10 | Separate test PMD rules retain correctness/security checks and permit fixture literals/MockMvc conventions. Production PMD rules are unchanged; `build -x test` passes.                                                                                                                                           |
| FAC-32 | Flyway V1 is the only schema source; setup migrates before insertion. Production and tests enforce FKs and share migrations; SQLite integration checks the usage ordering index and rejected orphan inserts.                                                                                                      |
| FAC-5  | Canonical epoch milliseconds, exclusive next-day dateTo and stable newest-first ordering. `LegacyMigrationIntegrationTest` converts mixed TEXT/INTEGER fixtures and checks API counts/order plus an API-created ticket; `PortalIntegrationTest` covers midnight/fractional/boundless filters and SQL comparisons. |
| FAC-6  | Aware UTC seed clock and Hibernate JDBC UTC. Python tests compare identical seeded rows across UTC/Asia/Singapore hosts; API integration checks returned ISO instants and UTC date aggregation. Existing browser local-time rendering is retained.                                                                |
| FAC-22 | One seed clock and capped usage/ticket times. Python tests verify determinism and every timestamp at UTC midnight; full fresh seed has zero future usage/ticket rows.                                                                                                                                             |
| FAC-7  | Customer delete rejects each dependent type with 409; database enforces references too. SQLite/controller tests keep ticket/usage endpoints working; page tests and E2E preserve the dialog and show the conflict.                                                                                                |
| FAC-8  | Used plan delete returns 409 with deactivation guidance. SQLite/controller tests preserve revenue and permit deactivation; page tests and E2E show the conflict.                                                                                                                                                  |
| FAC-21 | Chart counts and revenue group by plan ID with display names preserved. SQLite/controller regression creates duplicate plan names at different prices; E2E aggregate cross-checks use IDs and explicitly skip truncated fixtures.                                                                                 |
| FAC-9  | Bean Validation, field-specific enum/date/reference errors, RFC 9457 responses and duplicate-email 409 (including SQLite's driver-specific exception wrapping). Parameterized MockMvc integration tests cover reported input cases; missing descriptions deliberately succeed under FAC-12's optional contract.   |
| FAC-12 | Optional nullable schema; omitted/null descriptions normalize to empty text and forms explicitly send empty text. MockMvc, dialog and E2E tests cover create and clearing on edit, including a visible rejected edit.                                                                                             |
| FAC-17 | Assignment requires a known customer; all unassigned statuses clear customer/time. MockMvc tests cover all 16 status transitions, unknown/missing customers and dashboard counts.                                                                                                                                 |
| FAC-20 | Reopening clears resolution time; resolved/closed transitions preserve it. MockMvc tests cover both reopening statuses and re-resolution.                                                                                                                                                                         |
| FAC-15 | Every dashboard response is checked independently, including shape/network failures. Hook, page and E2E tests keep healthy cards visible when a chart fails.                                                                                                                                                      |
| FAC-16 | Shared pagination advances only on success, aborts old generations and ignores stale results/errors. Tests exercise both hooks, failed-page retries, concurrent calls and filter changes; list pages offer retry without discarding loaded rows.                                                                  |
| FAC-18 | Bounded array lists sort by ID and return X-Total-Count; all three pages show a truncation notice. SQLite tests exceed customer/device/plan/customer-usage caps and page tests check visible totals. Full paging remains FAC-27.                                                                                  |

Verification:

- Backend: 54 tests passed, then the added UTC aggregate regression passed separately (55 distinct tests); `./gradlew --console=plain lint build formatCheck -x test` passed, including production/test PMD and SpotBugs.
- Frontend: 41 tests passed; ESLint/TypeScript, Prettier and production build passed.
- Browser: 12 targeted E2E checks passed (11 in the initial run, the dashboard check after scoping its locator to exclude Next's route announcer). These include API health, revenue cross-checks, delete conflicts, isolated chart failure and ticket create/clear.
- Fresh disposable setup: `pnpm run setup` created 8 plans, 500 customers, 600 devices, 219,557 usage rows and 250 tickets; `pnpm dev` served the app. All usage timestamps are INTEGER, future usage/ticket counts are zero, and foreign_key_check is empty. The usage plan reports `SCAN usage_records USING INDEX idx_usage_recorded` with no temporary sort.
- Six Python tests cover conversion inventory, row/source preservation, idempotency, explicit naive-timezone requirement, DST ambiguity/gaps, rejected orphans, UTC seed determinism and future-time bounds.

Operational notes:

- Use `pnpm run setup`: the literal `pnpm setup` is pnpm's reserved shell-configuration command, so it cannot invoke the repository script. README now documents the correct command.
- Retained naive timestamps require confirmation of their original source timezone before conversion. No such decision was needed for the disposable verification database, and no retained data was changed. Conversion targets a new file; rollback and explicit Flyway adoption are documented in README.
- CI results are not monitored; the required local build/check commands passed. No merge or deployment was performed.

Integration with latest main (authentication and role permissions):

- Conflict resolution retains authenticated/CSRF-aware requests alongside isolated dashboard failures and shared pagination with stale-response protection. Page permission checks and visible delete conflicts/truncation coexist.
- Security integration tests now run Flyway against a disposable SQLite file with foreign keys and canonical timestamp settings; the duplicate schema fixture was removed. The authenticated admin dependent-delete regression expects 409, and ticket editing uses the validated priority field.
- Reverified: 79 backend tests, 64 frontend tests, six Python tests and 24 targeted E2E checks (including two login setup checks); backend lint/build/format, frontend ESLint/TypeScript/Prettier and production build all pass. Fresh disposable migration/seeding succeeds with 219,557 usage records. No retained database was changed.
