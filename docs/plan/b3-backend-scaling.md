# B3. Backend scaling

- **Track:** B (production migration)
- **Branch:** `plan/b3-backend-scaling`
- **Depends on:** B1.

## Goal

Run several backend replicas and keep list and filter requests fast at medium scale.

## Tasks

1. Move sessions to PostgreSQL with Spring Session JDBC, so any replica can serve any user.
2. Replace the 1,000-row lists for customers, devices, plans and tickets with server-side search,
   filter, sort and paging (`?q=&status=&sort=&cursor=`).
3. Use cursor (keyset) paging on `(recorded_at, id)` for usage. Make exact totals optional and use
   estimates for very large results.
4. Read the dashboard from the B1 rollup tables. Cache with Caffeine (30 to 60 s) and add ETags to
   list responses.
5. Version the API under `/api/v1` and keep the OpenAPI spec behind login.
6. Set timeouts and graceful shutdown: request timeout, database statement timeout and
   `server.shutdown: graceful`.
7. Limit request body size and validate paging parameters.

## Done when

- With 200 concurrent users on the B0 dataset: p95 under 200 ms for lists and the dashboard, under
  300 ms for filtered usage, error rate below 0.1%.
- Killing one replica mid-test drops no sessions and causes no user-visible errors beyond retried
  requests.
