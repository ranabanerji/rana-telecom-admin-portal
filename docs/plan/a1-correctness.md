# A1. Correctness and reliability

- **Track:** A (hardened demo)
- **Branch:** `plan/a1-correctness`
- **Depends on:** nothing. Do this first.

## Goal

Stop intermittent 500 errors under concurrent load and make SQLite handle concurrent writes
predictably.

## Evidence

- 1,200 parallel `GET /api/dashboard/revenue-by-plan` requests: `main` (Spring Boot 4.0.0,
  Hibernate 7.1.8) returned 373 errors; Spring Boot 4.0.10 with Hibernate 7.2.25 returned 76.
- Stack trace: `ArrayIndexOutOfBoundsException` in `GregorianCalendar.computeFields`, called from
  `JDBC3ResultSet.getTimestamp(int, Calendar)` (sqlite-jdbc) via
  `TimestampUtcAsJdbcTimestampJdbcType`.
- Hibernate passes one static `UTC_CALENDAR` to `getTimestamp`; sqlite-jdbc calls
  `setTimeInMillis` on it. sqlite-jdbc 3.54.1.0 has the same code, so upgrading does not help.
- The trigger is `hibernate.jdbc.time_zone: UTC` in `backend/src/main/resources/application.yaml`.
- `application.yaml` sets no WAL mode or busy timeout; HikariCP uses its default pool of 10.

## Tasks

1. Remove `hibernate.jdbc.time_zone: UTC`. Map `Instant` columns stored as epoch milliseconds with
   a stateless `AttributeConverter<Instant, Long>` (or a custom `JdbcType` that calls `getLong`).
2. Keep the FAC-5/FAC-6 guarantees: stored values stay UTC epoch milliseconds and API output stays
   ISO-8601 UTC. Existing `PortalIntegrationTest` and `LegacyMigrationIntegrationTest` must pass
   unchanged.
3. Add a regression test that fires at least 40 parallel requests at each dashboard endpoint and
   at `/api/customers`, and fails on any non-200 response.
4. Add `journal_mode=WAL` and `busy_timeout=5000` to the JDBC URL defaults (`TELECOM_JDBC_URL` and
   `DatabaseMigration`).
5. Limit the pool so writes serialize cleanly (for example `maximum-pool-size: 4`, with writes
   wrapped in short transactions). Document the single-writer limit.
6. At startup, read `PRAGMA journal_mode`, `foreign_keys` and `busy_timeout` and fail fast if they
   are wrong.

## Done when

- The parallel test passes 10 runs in a row with zero errors.
- A concurrent write test (for example 20 parallel ticket creates) completes with no
  `SQLITE_BUSY` errors.
- All backend, frontend, Python and end-to-end tests pass.

## Out of scope

- Moving off SQLite (see B1).
