# B1. Database: PostgreSQL

- **Track:** B (production migration)
- **Branch:** `plan/b1-database`
- **Depends on:** B0. A5 makes this smaller.

## Goal

Replace SQLite with PostgreSQL so the backend can handle medium-scale write volume and run as
several instances.

## Recommendation

PostgreSQL 16 or later, managed where the platform offers it (RDS, Cloud SQL, Azure Database). Keep
Flyway as the only schema source.

## Tasks

1. Port the schema as a PostgreSQL migration set: `bigint` identity keys, `timestamptz`,
   `numeric(12,2)` for money, check constraints for status columns, and the existing foreign keys,
   unique keys and indexes.
2. Partition `usage_records` by month on `recorded_at`. Create partitions ahead of time (pg_partman
   or a scheduled job), add `(customer_id, recorded_at desc)` per partition, and add a unique
   `event_id` for idempotent ingest (B6).
3. Add rollup tables for the dashboard (daily usage by type, active customers and revenue by plan),
   refreshed incrementally.
4. Run backend tests on PostgreSQL with Testcontainers; port `scripts/seed-database.py`.
5. Add Docker Compose for local PostgreSQL. SQLite stays available only for quick demos.
6. Write a one-time SQLite-to-PostgreSQL migration tool following `migrate-legacy-database.py`:
   read-only source, dry run, per-table row counts and checksums.
7. Size HikariCP (start at 20 per replica) and add PgBouncer only if the replica count needs it.
8. Turn on automated backups and point-in-time recovery; test a restore.

## Done when

- All backend, end-to-end and Python tests pass on PostgreSQL.
- On the B0 synthetic dataset, every list and filter query plan uses an index or partition pruning.
- A restore from backup completes within the agreed recovery target.

## Risks

- Timestamp semantics: migrate epoch milliseconds to `timestamptz` exactly once and check boundary
  dates (FAC-5) after migration.
