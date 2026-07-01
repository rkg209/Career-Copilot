# Spec: Phase 0 — Foundation & Walking Skeleton

## Header

| Field | Value |
|---|---|
| Phase | 0 — Foundation & Walking Skeleton |
| Status | IMPLEMENTED |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Implementation note (2026-07-02):** All Phase 0 features implemented, tested (Testcontainers),
> CI green, and deployed to DigitalOcean (first deploy done). Status flipped APPROVED → IMPLEMENTED
> retroactively — the flip was missed at phase close.

---

## Goal

Stand up the full deployment pipeline end-to-end with a minimal but real application: a Spring Boot 3.5 backend with JWT authentication, a PostgreSQL+pgvector database, a React/TypeScript frontend shell, a passing CI pipeline, and a live DigitalOcean deployment. The goal is to prove the entire path from local dev to production works before any feature work begins.

---

## Scope

### In Scope
- Spring Boot 3.5 project skeleton with Maven Wrapper, package-by-feature structure under `com.careercopilot`
- PostgreSQL database with pgvector extension via Testcontainers (dev) and DigitalOcean Managed Postgres (prod)
- Flyway migrations: V1 initial schema (users table), V2 pgvector extension
- JWT-based auth: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me`
- Spring Security filter chain wired (public: register/login; protected: everything else)
- `GET /actuator/health` endpoint
- React + TypeScript SPA shell with login/register UI, JWT stored in httpOnly cookie or localStorage
- GitHub Actions CI: compile, test, spotless check on every push to `main`
- DigitalOcean App Platform deploy from `main` branch (auto-deploy on green CI)
- Namecheap domain pointed at DO app

### Out of Scope
- Any job scraping, resume tailoring, scoring, or tracker features
- LLM integration (no LlmGateway in this phase)
- File uploads or PDF rendering
- Email verification

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-0.1 | Users can register with email + password |
| FR-0.2 | Users can log in and receive a JWT |
| FR-0.3 | Protected endpoints reject unauthenticated requests with 401 |
| NFR-0.1 | Application starts cleanly with `./mvnw spring-boot:run` after `docker compose up db` |
| NFR-0.2 | CI pipeline passes on every push to `main` |
| NFR-0.3 | Production deployment reachable at configured domain |

---

## Data Model Changes

### New Tables

```sql
-- V1__initial_schema.sql
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

```sql
-- V2__enable_pgvector.sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V1 | `V1__initial_schema.sql` | Create users table |
| V2 | `V2__enable_pgvector.sql` | Enable pgvector extension |

---

## API Changes

### New Endpoints

#### `POST /api/v1/auth/register`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "string (min 8 chars)"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "email": "user@example.com"
}
```

**Error cases:**
- `400` — validation failure (missing fields, password too short)
- `409` — email already registered

---

#### `POST /api/v1/auth/login`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**Response (200):**
```json
{
  "token": "jwt-string",
  "expiresIn": 86400
}
```

**Error cases:**
- `401` — invalid credentials

---

#### `GET /api/v1/auth/me`

**Headers:** `Authorization: Bearer {token}`

**Response (200):**
```json
{
  "id": "uuid",
  "email": "user@example.com"
}
```

**Error cases:**
- `401` — missing or invalid token

---

#### `GET /actuator/health`

**Response (200):**
```json
{
  "status": "UP"
}
```

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `AuthController` | `com.careercopilot.auth` | Register, login, me endpoints |
| `AuthService` | `com.careercopilot.auth` | User registration, password hashing, JWT issuance |
| `UserRepository` | `com.careercopilot.auth` | Spring Data JPA repo for User entity |
| `User` | `com.careercopilot.auth` | JPA entity |
| `RegisterRequestDto` | `com.careercopilot.auth.dto` | Incoming register payload |
| `LoginRequestDto` | `com.careercopilot.auth.dto` | Incoming login payload |
| `AuthResponseDto` | `com.careercopilot.auth.dto` | Outgoing token payload |
| `UserResponseDto` | `com.careercopilot.auth.dto` | Outgoing user info |
| `JwtService` | `com.careercopilot.shared` | JWT sign and verify (JJWT or nimbus-jose-jwt) |
| `JwtAuthFilter` | `com.careercopilot.shared` | Spring Security OncePerRequestFilter |
| `SecurityConfig` | `com.careercopilot.shared` | SecurityFilterChain bean |

### Interaction Flow

```
POST /api/v1/auth/register
  → AuthController.register(RegisterRequestDto)
  → AuthService.register(dto)
    → UserRepository.existsByEmail(email) — 409 if exists
    → PasswordEncoder.encode(password)
    → UserRepository.save(new User(...))
  → returns UserResponseDto (201)

POST /api/v1/auth/login
  → AuthController.login(LoginRequestDto)
  → AuthService.login(dto)
    → UserRepository.findByEmail(email) — 401 if not found
    → PasswordEncoder.matches(raw, hash) — 401 if mismatch
    → JwtService.generateToken(user)
  → returns AuthResponseDto (200)

GET /api/v1/auth/me
  → JwtAuthFilter extracts + validates JWT, sets SecurityContext
  → AuthController.me(Principal)
  → AuthService.getByEmail(principal.name)
  → returns UserResponseDto (200)
```

---

## LLM Tasks

*(No LLM tasks in Phase 0.)*

---

## Rendering Tasks

*(No rendering tasks in Phase 0.)*

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `AuthServiceTest` | Register (success, duplicate email), login (success, bad password), JWT round-trip |
| `JwtServiceTest` | Token generation, validation, expiry |

### Integration Tests

| Test class | What it tests |
|---|---|
| `AuthIntegrationTest` | Full register→login→me flow via MockMvc + Testcontainers Postgres |
| `SecurityIntegrationTest` | Protected endpoint returns 401 without token, 200 with valid token |

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` — all tests green
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] V1 and V2 migrations reviewed (additive only — they are new tables/extensions)
- [ ] No secrets committed (JWT secret, DB password must be in env vars only)
- [ ] Required env vars added to DigitalOcean App spec

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Postgres JDBC URL | `jdbc:postgresql://host:5432/careercopilot` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `careercopilot` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `{secret}` |
| `JWT_SECRET` | HMAC-SHA256 signing key (min 32 bytes) | `{secret}` |
| `JWT_EXPIRY_SECONDS` | Token lifetime | `86400` |

### DigitalOcean Deploy Steps

1. Push to `main` — GitHub Actions CI runs compile + test + spotless.
2. On green CI: DigitalOcean App Platform auto-deploys Docker image.
3. Flyway runs V1 + V2 migrations on app startup.
4. Smoke test: `curl https://{domain}/actuator/health` → `{"status":"UP"}`.
5. Register a user, log in, call `/api/v1/auth/me` — verify 200.

---

## Definition of Done

- [x] Spec status set to APPROVED before implementation begins
- [x] `POST /api/v1/auth/register` works end-to-end
- [x] `POST /api/v1/auth/login` returns a valid JWT
- [x] `GET /api/v1/auth/me` returns user info with valid JWT, 401 without
- [x] V1 + V2 Flyway migrations apply cleanly
- [x] `AuthIntegrationTest` and `SecurityIntegrationTest` pass with Testcontainers
- [x] GitHub Actions CI passes on `main`
- [x] React shell renders login/register UI (no 404 on root)
- [x] App live on DigitalOcean at configured domain
- [x] `/review` checklist passes
- [x] CLAUDE.md updated to Phase 1
- [x] Spec status set to IMPLEMENTED
