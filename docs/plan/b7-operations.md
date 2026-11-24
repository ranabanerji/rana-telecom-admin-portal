# B7. Operations

- **Track:** B (production migration)
- **Branch:** `plan/b7-operations`
- **Depends on:** B1. Finishes last.

## Goal

Ship signed images, see failures quickly, and recover from them with tested runbooks.

## Tasks

1. **Images.** Minimal non-root images for the backend (distroless or slim JRE) and the frontend
   (slim Node, standalone build). Scan with Trivy, sign with cosign, and publish a CycloneDX SBOM.
2. **Deployment config.** Production-style Docker Compose now; a Helm chart or Terraform module
   once B0 picks the platform. All configuration comes from the environment.
3. **Observability.** OpenTelemetry traces across frontend, backend, database and Kafka;
   Prometheus metrics; JSON logs with trace IDs; Grafana dashboards.
4. **SLOs and alerts.** API availability 99.9%, p95 latency targets from B3, ingest lag under 60 s,
   and an error budget dashboard.
5. **Runbooks.** Backup and point-in-time restore, partition maintenance, secret rotation,
   dead-letter replay, and rollback.

## Done when

- Images are signed and scanned in CI, with no high or critical vulnerabilities.
- Alerts fire in a game-day test for API errors, latency, and ingest lag.
- Each runbook has been followed once end to end.
