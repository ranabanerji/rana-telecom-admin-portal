# A2. Performance (demo scale)

- **Track:** A (hardened demo)
- **Branch:** `plan/a2-performance`
- **Depends on:** A1 (the parallel test from A1 is reused here).

## Goal

Cut dashboard and list latency with changes that need no new infrastructure.

## Evidence

- `DashboardController.getStats` and `getRevenueByPlan` call
  `customerRepository.findByStatus(ACTIVE)` and sum or group the result in Java.
- `Customer.plan`, `Device.customer`, `SupportTicket.customer` and `UsageRecord.customer` are all
  `FetchType.EAGER`.
- Spring logs the `spring.jpa.open-in-view is enabled by default` warning at startup.
- Every controller method logs at INFO on each request.
- No response compression is configured.

## Tasks

1. Replace the Java-side sums with JPQL aggregates: `SUM(p.monthlyPrice)` joined over active
   customers, and `GROUP BY plan.id` for revenue by plan.
2. Switch the four relationships to `FetchType.LAZY`. Add `JOIN FETCH` queries or `@EntityGraph`
   where list responses need the related name.
3. Set `spring.jpa.open-in-view: false` and fix any lazy-loading errors this exposes.
4. Turn on `server.compression.enabled` for JSON responses and `spring.threads.virtual.enabled`.
5. Move per-request controller logs to DEBUG. Keep INFO for writes (create, update, delete).
6. Cache the plan list with Caffeine (60 s TTL), evicted on plan create, update and delete.
7. Record k6 (or similar) p50 and p95 for every dashboard and list endpoint before and after.

## Done when

- Dashboard endpoints p95 under 100 ms on the standard seeded data (500 customers, about 220k usage
  rows).
- Query count per list request is constant (no N+1), checked with Hibernate statistics in a test.
- All existing tests pass.

## Out of scope

- Server-side search and paging for customers and devices (see B3).
