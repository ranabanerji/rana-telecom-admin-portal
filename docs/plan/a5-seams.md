# A5. Seams for a later production move

- **Track:** A (hardened demo)
- **Branch:** `plan/a5-seams`
- **Depends on:** A1.

## Goal

Remove the parts of the code that tie it to SQLite or to local-only running, so B1 and B3 are
smaller.

## Evidence

- `UsageRecordRepository.sumByTypeAndDate` uses `date(recorded_at / 1000.0, 'unixepoch')` and
  `CustomerRepository.countByMonth` uses `strftime`. Both are native SQLite SQL, and today only
  `PortalIntegrationTest` calls them.
- `ApiExceptionHandler.sqliteIntegrity` checks SQLite error code 19 directly.
- There is no Actuator, so there are no readiness checks or metrics.
- Configuration is spread across `application.yaml` defaults and README notes.

## Tasks

1. Rewrite `sumByTypeAndDate` and `countByMonth` without native SQL (JPQL with Java-side date
   bucketing, or remove them if no feature needs them).
2. Move database-specific constraint detection into one `ConstraintViolationClassifier` with a
   SQLite implementation, so PostgreSQL can add its own.
3. Add `spring-boot-starter-actuator`: expose `health` (with liveness and readiness groups) and
   `prometheus` locally, nothing else.
4. Make every setting overridable by an environment variable and list them in `.env.example`
   (database URL, demo accounts, cookie flags, CORS and backend URL).
5. Make Flyway migrations avoid SQLite-only syntax where a portable form exists, and note any that
   cannot.

## Done when

- `rg -n "nativeQuery = true"` returns nothing, or only queries listed as SQLite-specific in a
  comment.
- `/actuator/health/readiness` reports `UP` and checks the database.
- `.env.example` covers every variable the app reads.

## Out of scope

- Adding PostgreSQL itself (see B1).
