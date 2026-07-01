# Spec: Phase 8 — Hardening, Metrics & Polish

## Header

| Field | Value |
|---|---|
| Phase | 8 — Hardening, Metrics & Polish |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Schema reconciliation note:** This phase adds no user-facing tables; any new table is a read-model
> or metrics view. `user_id` references remain `UUID`. Confirm before APPROVED.

---

## Goal

Make the system defensible, measured, and demo-ready. This phase wires up the full metric set (ATS
match-score improvement — the headline number — plus latency p50/p99, tokens per tailoring, and
retrieval precision@k), adds observability (structured logging, Sentry, actuator metrics), rounds out
guardrail and anonymization test coverage, publishes OpenAPI docs, produces an architecture diagram,
and polishes the UI for a live demo. No major new user features — this is the closing quality phase.

---

## Scope

### In Scope
- Metrics endpoint(s): ATS-improvement aggregate (before/after coverage delta across the labeled set
  and across real user runs), tokens/tailoring, latency percentiles, retrieval precision@k report.
- Observability: Sentry integration (backend + frontend), structured JSON logging with `traceId`,
  Micrometer/actuator metrics exposed at `/actuator/metrics` + Prometheus scrape endpoint.
- Guardrail + anonymization coverage hardening: raise test coverage; add adversarial cases
  (injection corpus, PII-leak corpus, grounding-violation corpus).
- OpenAPI docs served at `/api/v1/docs` (springdoc) matching `planning/05-openapi.yaml`.
- Architecture diagram (checked into `planning/` / README) and a demo script.
- Performance pass: DB index review, N+1 query audit, connection-pool + Tectonic timeout tuning.
- Security pass: dependency scan, CORS/JWT review, error-response leak review, rate-limit config doc.
- Frontend polish: loading/empty/error states, responsive layout, accessibility basics.

### Out of Scope
- New user-facing features (all product surface was delivered in Phases 1–7).
- Multi-region / horizontal scaling (single-instance DO App Platform remains the target).
- Refresh-token auth (explicitly deferred to a future v2 per API design §2.4).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-8.1 | The system reports before/after ATS coverage improvement (the headline metric). |
| FR-8.2 | An authenticated user can retrieve aggregate ATS-improvement metrics (no PII). |
| NFR-8.1 | Latency p50/p99 for tailoring and scoring is measured and exposed. |
| NFR-8.2 | Token usage per tailoring run is queryable from `token_usage_log`. |
| NFR-8.3 | Retrieval precision@k is measured against a labeled retrieval set. |
| NFR-8.4 | Errors are forwarded to Sentry with a `traceId` correlating to logs. |
| NFR-8.5 | Guardrail + anonymization suites cover adversarial inputs, not just happy paths. |
| NFR-8.6 | OpenAPI docs are served and match the deployed endpoints. |

---

## Data Model Changes

No new user data tables. Metrics are computed over existing tables (`token_usage_log`,
`scoring_results`, `resume_versions`). If a materialized read-model is needed for the ATS-improvement
aggregate, add it as an additive migration:

```sql
-- V17__metrics_ats_improvement_view.sql   (optional; add only if query cost warrants it)
CREATE VIEW ats_improvement_v AS
SELECT sr.user_id,
       sr.job_posting_id,
       min(sr.coverage_score) AS first_score,
       max(sr.coverage_score) AS best_score,
       max(sr.coverage_score) - min(sr.coverage_score) AS improvement,
       count(*) AS score_runs
FROM scoring_results sr
GROUP BY sr.user_id, sr.job_posting_id;
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V17 (optional) | `V17__metrics_ats_improvement_view.sql` | Aggregate view for ATS-improvement metric |

---

## API Changes

### `GET /api/v1/metrics/ats-improvement` — AUTHENTICATED
Aggregate only; contains no PII and no other user's identity.
**Response (200):**
```json
{
  "labeledSet": { "jdCount": 20, "meanBaselineCoverage": 0.41, "meanTailoredCoverage": 0.78, "meanImprovement": 0.37 },
  "liveUsers": { "scoredResumes": 134, "meanImprovement": 0.29 },
  "generatedAt": "2026-07-02T10:30:00Z"
}
```
**Error cases:** `401 AUTHENTICATION_REQUIRED`.

### `GET /api/v1/docs` — PUBLIC
Serves the OpenAPI 3.1 document (springdoc). Swagger UI at `/api/v1/docs/swagger-ui`.

### Observability endpoints (ops, not product)
- `GET /actuator/health`, `/actuator/metrics`, `/actuator/prometheus` — exposed (health public;
  detailed metrics restricted to an ops role / internal network).

No breaking changes to any existing endpoint; all additions are additive per the versioning policy.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `MetricsController` | `com.careercopilot.metrics` | ATS-improvement + aggregate metric endpoints |
| `AtsImprovementService` | `com.careercopilot.metrics` | Compute labeled-set + live deltas |
| `RetrievalEvalService` | `com.careercopilot.metrics` | precision@k over a labeled retrieval set (offline/report) |
| `TokenUsageReportService` | `com.careercopilot.metrics` | Tokens/tailoring, cost attribution from `token_usage_log` |
| `SentryConfig` / `ObservabilityConfig` | `com.careercopilot.shared` | Sentry init; Micrometer tags; traceId MDC filter |
| `OpenApiConfig` | `com.careercopilot.shared` | springdoc info/servers/security scheme |
| DTOs | `com.careercopilot.metrics.dto` | `AtsImprovementReport`, `TokenUsageReport` |

### Interaction Flow

```
GET /metrics/ats-improvement
  → MetricsController.atsImprovement(principal)
  → AtsImprovementService.report()
    → labeled set: read stored eval results (Phase 4 harness output)
    → live: aggregate scoring_results (min→max coverage per user+job)
  → returns AtsImprovementReport (aggregate, no PII)

Cross-cutting:
  · TraceIdFilter puts a traceId in MDC → structured logs + error envelope + Sentry event
  · Micrometer @Timed on tailoring/scoring services → p50/p99 histograms at /actuator/prometheus
```

---

## LLM Tasks

N/A for this phase — no new LLM generation. Existing LLM calls are unchanged; this phase only measures
and hardens them (token/latency reporting, adversarial guardrail tests).

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `AtsImprovementServiceTest` | Baseline→tailored delta math over seeded scoring rows |
| `RetrievalEvalServiceTest` | precision@k computed correctly against labeled expected sets |
| `TokenUsageReportServiceTest` | Per-task token aggregation from `token_usage_log` |

### Integration Tests

| Test class | What it tests |
|---|---|
| `MetricsIntegrationTest` | `GET /metrics/ats-improvement` returns aggregate, no PII, 401 unauth |
| `OpenApiDocsIntegrationTest` | `/api/v1/docs` served; lists all expected endpoints |
| `ObservabilityIntegrationTest` | Error response carries `traceId`; actuator metrics exposed |

### Guardrail / Adversarial Tests (hardening)

| Test | Scenario |
|---|---|
| `InjectionCorpusTest` | A corpus of injection payloads in JD input is neutralized |
| `PiiLeakCorpusTest` | A corpus of PII-laden logs anonymizes with zero leakage |
| `GroundingViolationCorpusTest` | A corpus of invented-experience outputs is rejected |

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] Dependency vulnerability scan clean (or documented/accepted)
- [ ] Sentry DSN configured (backend + frontend) as secrets
- [ ] OpenAPI doc matches deployed endpoints
- [ ] `$200` DO credit consumption reviewed; LLM budget within ~$25/mo
- [ ] Architecture diagram + demo script committed

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `SENTRY_DSN` | Backend Sentry DSN | `{secret}` |
| `VITE_SENTRY_DSN` | Frontend Sentry DSN | `{secret}` |
| `MANAGEMENT_METRICS_ROLE` | Role allowed to read detailed actuator metrics | `ops` |

### DigitalOcean Deploy Steps
1. Push to `main`; green CI (tests + adversarial suites + doc check).
2. DO auto-deploys; Flyway applies V17 (if included).
3. Smoke: `GET /metrics/ats-improvement` (authed) returns the headline delta; `/api/v1/docs` loads;
   trigger a handled error and confirm the Sentry event + `traceId` correlation.
4. Final demo walkthrough end-to-end (register → job → profile → tailor → score → track → intel → practice).

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] ATS-improvement metric endpoint returns the labeled-set + live headline delta (no PII)
- [ ] Latency p50/p99 and tokens/tailoring exposed via actuator/metrics endpoints
- [ ] Retrieval precision@k measured against a labeled set
- [ ] Sentry receives errors with `traceId` correlating to structured logs
- [ ] Adversarial guardrail + anonymization corpora pass
- [ ] OpenAPI docs served at `/api/v1/docs` and match deployed endpoints
- [ ] Architecture diagram + demo script committed
- [ ] V17 migration (if used) applies cleanly
- [ ] All unit + integration + adversarial tests pass with Testcontainers
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated (project complete / maintenance mode)
- [ ] Deployed to DigitalOcean and full end-to-end demo smoke-tested
- [ ] Spec status set to IMPLEMENTED
