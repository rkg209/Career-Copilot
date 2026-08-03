# Rate limiting (config doc only — no in-app limiter)

Career Copilot has no application-level rate limiter (no bucket4j, no Redis-backed token bucket).
That's a deliberate Phase 8 scope decision: at current traffic (single DO App Platform instance,
free-tier LLM models), the cost of a limiter isn't justified yet. This doc records the
recommendation for when it is — either as a proxy/edge concern or, if traffic grows enough to
need per-user quotas, an in-app one.

## Where to enforce it

Prefer an edge/proxy layer over an in-app filter: DigitalOcean App Platform sits behind a
managed load balancer, and rate limiting there means bad traffic never reaches the JVM. If a
proxy layer isn't available, an `nginx` sidecar or a Spring `Filter` (ordered after
`TraceIdFilter`, before `JwtAuthFilter`) are the fallback options, in that order of preference.

## Recommended limits

| Route group | Limit | Why |
|---|---|---|
| `/api/v1/auth/register`, `/api/v1/auth/login` | 10 requests / minute / IP | Brute-force and credential-stuffing surface — unauthenticated, so IP is the only signal available. |
| `/api/v1/tailoring/tailor`, `/api/v1/tailoring/cover-letter` | 10 requests / minute / user | LLM-backed; each call costs real tokens against the ~$25/mo budget (see CLAUDE.md Cost Discipline). |
| `/api/v1/scoring/score` | 20 requests / minute / user | LLM-backed (EXTRACTION tier), cheaper than tailoring but still a paid call. |
| `/api/v1/trainer/generate`, `/api/v1/trainer/questions/{id}/answer` | 20 requests / minute / user | LLM-backed (TAILORING / SCORING_AUGMENTATION tiers). |
| Everything else under `/api/v1/**` | 120 requests / minute / user (or / IP for public GETs) | Generic abuse ceiling; DB-only reads are cheap but not free. |
| `/actuator/prometheus`, `/actuator/metrics` | 30 requests / minute / IP | Scrape endpoints are `authenticated()` (see `SecurityConfig`) but still worth capping against a compromised token. |

## Why not now

- Every LLM-backed endpoint already goes through `LlmGateway`/`EmbeddingGateway`, which log
  token usage per call (`token_usage_log`) — the Phase 8 `/api/v1/metrics/tokens` endpoint makes
  runaway usage visible before it becomes a rate-limiting emergency.
- `MANAGEMENT_METRICS_ROLE` was dropped from scope (no roles column exists — see the Phase 8 spec
  correction #3), so today's practical mitigation for the actuator endpoints is the network
  restriction below, not a role check.

## Actuator network restriction (recommended, not yet applied)

`/actuator/prometheus` and `/actuator/metrics` are `authenticated()` (any logged-in user) rather
than gated by a role, since there's no roles column to check. Until one exists, restrict scraping
to the DO internal network or a specific operator IP range at the load-balancer/firewall level
rather than relying on JWT auth alone.
