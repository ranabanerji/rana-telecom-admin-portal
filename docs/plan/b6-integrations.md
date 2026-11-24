# B6. Integrations

- **Track:** B (production migration)
- **Branch:** `plan/b6-integrations`
- **Depends on:** B1 (partitioned usage, `event_id`) and B4 (secrets, TLS).

## Goal

Ingest usage records from upstream systems and keep billing, CRM, webhooks and notifications in
sync, without losing or duplicating events.

## Recommendation

Kafka with Schema Registry, and a transactional outbox for everything the app sends out.

## Tasks

1. **Usage ingest.** Consume `usage.cdr.v1` (Avro or Protobuf, checked by Schema Registry). Write in
   batches, skip duplicates using `event_id`, update the rollup tables in the same transaction, and
   send failed messages to a dead-letter topic with a replay tool.
2. **Transactional outbox.** Each domain change writes an `outbox` row in the same transaction. A
   relay (Debezium or a polling publisher) publishes to `customer.events.v1` and
   `ticket.events.v1`, at least once.
3. **Billing and CRM sync.** Adapters consume domain events and call vendor APIs with idempotency
   keys, retries with backoff and a circuit breaker (Resilience4j). A daily reconciliation report
   compares both sides.
4. **Outbound webhooks.** Admins register endpoints and event types. Payloads are signed with
   HMAC-SHA256 and a timestamp; failed deliveries retry with backoff; the delivery log is visible in
   the UI.
5. **Notifications.** Email and Slack for events such as high-priority tickets and ingest failures,
   with opt-in per user and rate limits.
6. **Tests.** Testcontainers for Kafka and Schema Registry; consumer-driven contract tests for the
   event schemas.

## Done when

- Ingest holds 1,000 events/s for 30 minutes with consumer lag under 30 s and zero duplicates.
- Killing the consumer mid-run loses no data.
- Webhook signatures verify with a reference receiver.
