# B0. Readiness gate

- **Track:** B (production migration)
- **Branch:** `plan/b0-readiness`
- **Depends on:** a decision to run the app with real users or real data.

## Goal

Make the decisions that shape every later stage, and record them before any production work
starts.

## Decisions to record

| Decision                | Recommendation                                                   | Affects           |
| ----------------------- | ---------------------------------------------------------------- | ----------------- |
| Hosting platform        | Kubernetes or managed containers; stay portable until decided    | B1, B4, B6, B7    |
| Data classification     | Treat customer records as personal data (PII)                    | B1, B4, retention |
| User and role ownership | Google Workspace for identity; app-managed ADMIN and AGENT roles | B2                |
| Service targets         | 99.9% availability; p95 under 200 ms for lists and the dashboard | B3, B5, B7        |
| Recovery targets        | 15 minutes of data loss (RPO), 1 hour to recover (RTO)           | B1, B7            |
| Usage data retention    | 13 months online; older partitions archived to object storage    | B1, B6            |
| Billing and CRM systems | Vendor, API style and rate limits                                | B6                |
| Notification channels   | Email provider and Slack workspace                               | B6                |

## Tasks

1. Write one architecture decision record (ADR) per decision under `docs/adr/`.
2. Build a synthetic medium-scale dataset generator: 1M customers, 100M usage rows per month, 500
   staff users. Both B1 and the go-live checklist use it.
3. Write k6 scenarios for the main user journeys and run a baseline on the hardened demo.
4. Update `SECURITY.md` with the intended production scope (still marked as not yet production).

## Done when

- Every decision in the table has an accepted ADR.
- Baseline k6 numbers are recorded.
