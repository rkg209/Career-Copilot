# Career Copilot — API Design

**Document version:** 1.0
**Status:** Implementation-Ready
**Builds on:** `architecture.md` v1.0, `system-design.md` v1.0, `database-design.md` v1.0

---

## Table of Contents

1. [Design Philosophy](#1-design-philosophy)
2. [Authentication Scheme](#2-authentication-scheme)
3. [Error Handling Conventions](#3-error-handling-conventions)
4. [Pagination Strategy](#4-pagination-strategy)
5. [Versioning Policy](#5-versioning-policy)
6. [OpenAPI Specification](#6-openapi-specification)

---

## 1. Design Philosophy

The API follows four governing principles that resolve ambiguity in every design decision:

**1. The client is a single known consumer.** The React SPA is the only intended client. This justifies response shapes optimized for direct UI consumption (e.g., denormalized display fields, pre-computed status labels) rather than hypermedia generality. The API is not designed as a public platform API.

**2. Security context is server-authoritative.** The user identity embedded in the JWT is the only source of truth for ownership. No endpoint accepts a `userId` in the request body or as a path parameter to identify the acting user. Path parameters like `{id}` identify the resource being acted upon; the actor is always derived from the token.

**3. Errors are structured contracts.** Every non-2xx response carries a machine-readable error body. Clients must be able to branch on `errorCode` without parsing `message` strings. Message strings are for human developers, not for programmatic logic.

**4. Versioning is a compatibility guarantee, not a namespace.** The `/api/v1/` prefix is a promise: once a response field is published, it will not be removed or renamed within v1. Additive changes (new optional fields) are non-breaking and do not require a version bump.

---

## 2. Authentication Scheme

### 2.1 Mechanism

The API uses **stateless JWT Bearer authentication** implemented via Spring Security's filter chain. There are no sessions, no cookies, and no server-side session store. The only server-side state related to authentication is the `jwt_denylist` table, which records revoked token JTIs.

### 2.2 Token Structure

Tokens are signed **HS256 JWTs**. The signing secret is read from the `JWT_SECRET` environment variable at startup; the application refuses to start if this variable is absent or shorter than 32 bytes.

**Standard claims:**

| Claim | Type | Value |
|---|---|---|
| `iss` | string | `"career-copilot"` |
| `sub` | string | User ID as a decimal string (e.g., `"42"`) |
| `iat` | NumericDate | Token issuance time (Unix epoch seconds) |
| `exp` | NumericDate | Issuance time + `JWT_EXPIRY_SECONDS` (default: 86400) |
| `jti` | string | UUID v4; used as the denylist key |

**Private claims:**

| Claim | Type | Value |
|---|---|---|
| `email` | string | User's email address at time of issuance |
| `displayName` | string | User's display name at time of issuance |

Private claims are included for client-side display convenience only. The server never reads `email` or `displayName` from the token; it always queries the database for authoritative user data.

### 2.3 Token Lifecycle

```
Registration / Login
        │
        ▼
AuthService issues JWT
  - signs with HS256 + JWT_SECRET
  - sets exp = now() + JWT_EXPIRY_SECONDS
  - generates UUID v4 jti
        │
        ▼
Client stores token
  - localStorage or memory (SPA decision)
  - never in a cookie (no CSRF surface)
        │
        ▼
Every authenticated request
  Authorization: Bearer <token>
        │
        ▼
Spring Security filter chain
  1. Extract token from Authorization header
  2. Verify HS256 signature
  3. Check exp claim (reject if expired)
  4. Check jti against jwt_denylist
     SELECT 1 FROM jwt_denylist
     WHERE jti = ? AND expires_at > now()
     → if found: reject with 401 TOKEN_REVOKED
  5. Populate SecurityContextHolder with UserPrincipal { userId, email }
        │
        ▼
Logout
  POST /api/v1/auth/logout
  Authorization: Bearer <token>
        │
        ▼
AuthService.logout(token)
  - parse jti and exp from token
  - INSERT INTO jwt_denylist (jti, user_id, expires_at)
  - return 204 No Content
```

### 2.4 Token Refresh

There is no refresh token mechanism in v1. When a token expires, the client must re-authenticate via `POST /api/v1/auth/login`. This is an intentional simplification: the default expiry of 24 hours covers a full working session, and the single-consumer SPA can handle re-authentication transparently.

A refresh token flow may be introduced in v2 if session continuity requirements change.

### 2.5 Endpoint Authorization Matrix

Every endpoint has exactly one of three authorization levels:

| Level | Meaning | Spring Security expression |
|---|---|---|
| `PUBLIC` | No token required. Unauthenticated requests are accepted. | No `@PreAuthorize`; `permitAll()` in security config |
| `AUTHENTICATED` | Valid, non-expired, non-revoked token required. Ownership is not checked at the controller layer — it is enforced inside the service by comparing the resource's `user_id` to the principal's `userId`. | `@PreAuthorize("isAuthenticated()")` |
| `OWNER_ONLY` | Same as `AUTHENTICATED`, plus the service verifies that the resource belongs to the requesting user. A mismatch returns 403, not 404, to avoid leaking resource existence. | `@PreAuthorize("isAuthenticated()")` + service-layer ownership check |

**Endpoint authorization table:**

| Method | Path | Level | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | PUBLIC | |
| POST | `/api/v1/auth/login` | PUBLIC | |
| POST | `/api/v1/auth/logout` | AUTHENTICATED | Invalidates the presented token |
| GET | `/api/v1/jobs` | PUBLIC | Paginated listing |
| GET | `/api/v1/jobs/{id}` | PUBLIC | |
| GET | `/api/v1/jobs/search` | PUBLIC | |
| POST | `/api/v1/jobs/paste` | AUTHENTICATED | Creates a user-owned job posting |
| GET | `/api/v1/profile` | OWNER_ONLY | Returns the authenticated user's profile |
| PUT | `/api/v1/profile` | OWNER_ONLY | |
| GET | `/api/v1/profile/experience` | OWNER_ONLY | |
| POST | `/api/v1/profile/experience` | OWNER_ONLY | |
| PUT | `/api/v1/profile/experience/{id}` | OWNER_ONLY | |
| DELETE | `/api/v1/profile/experience/{id}` | OWNER_ONLY | Soft delete |
| POST | `/api/v1/tailoring/tailor` | OWNER_ONLY | Triggers LLM + render pipeline |
| GET | `/api/v1/tailoring/resumes` | OWNER_ONLY | Paginated |
| GET | `/api/v1/tailoring/resumes/{id}` | OWNER_ONLY | |
| GET | `/api/v1/tailoring/resumes/{id}/pdf` | OWNER_ONLY | Returns `application/pdf` |
| POST | `/api/v1/tailoring/cover-letter` | OWNER_ONLY | |
| POST | `/api/v1/scoring/score` | OWNER_ONLY | |
| GET | `/api/v1/scoring/results/{resumeVersionId}` | OWNER_ONLY | |
| GET | `/api/v1/tracker/applications` | OWNER_ONLY | Paginated |
| POST | `/api/v1/tracker/applications` | OWNER_ONLY | |
| PUT | `/api/v1/tracker/applications/{id}` | OWNER_ONLY | |
| GET | `/api/v1/tracker/applications/{id}/interview-logs` | OWNER_ONLY | |
| POST | `/api/v1/tracker/applications/{id}/interview-logs` | OWNER_ONLY | |
| POST | `/api/v1/tracker/applications/{id}/interview-logs/{logId}/contribute` | OWNER_ONLY | Privacy-critical |
| GET | `/api/v1/intel/companies` | PUBLIC | Paginated |
| GET | `/api/v1/intel/companies/{companyName}` | PUBLIC | |
| GET | `/api/v1/metrics/ats-improvement` | AUTHENTICATED | Aggregate only; no PII |

### 2.6 CORS Policy

The allowed origin is configured via the `CORS_ALLOWED_ORIGIN` environment variable (default: `http://localhost:5173` for local development). In production this is set to the deployed SPA origin. Wildcard origins (`*`) are never permitted on authenticated endpoints.

```
Allowed origins:   ${CORS_ALLOWED_ORIGIN}
Allowed methods:   GET, POST, PUT, DELETE, OPTIONS
Allowed headers:   Authorization, Content-Type, Accept
Exposed headers:   Content-Disposition   (for PDF download filename)
Allow credentials: false                 (Bearer tokens, not cookies)
Max age:           3600s
```

### 2.7 Rate Limiting

Rate limiting is applied at the reverse proxy layer (not in the Spring application) and is therefore outside the scope of this document. The application does not implement its own rate limiting. The following limits are documented here as the expected proxy configuration:

| Endpoint group | Limit |
|---|---|
| `POST /api/v1/auth/*` | 10 requests / minute / IP |
| `POST /api/v1/tailoring/*` | 20 requests / hour / user |
| `POST /api/v1/scoring/*` | 60 requests / hour / user |
| All other authenticated | 300 requests / minute / user |
| All public read | 120 requests / minute / IP |

---

## 3. Error Handling Conventions

### 3.1 Error Response Envelope

Every non-2xx response body conforms to the following JSON structure. This structure is the contract; clients must not parse `message` for branching logic.

```json
{
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Job posting with id 42 was not found.",
  "path": "/api/v1/jobs/42",
  "timestamp": "2025-01-15T10:30:00Z",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "details": []
}
```

**Field definitions:**

| Field | Type | Always present | Description |
|---|---|---|---|
| `errorCode` | string | Yes | Machine-readable error identifier. Stable within v1. See §3.2 for the full registry. |
| `message` | string | Yes | Human-readable description for developers. May change between releases; do not parse. |
| `path` | string | Yes | The request path that produced the error. |
| `timestamp` | string (ISO 8601) | Yes | UTC timestamp of the error. |
| `traceId` | string | Yes | Correlates with Sentry and application logs. Included in every error response regardless of environment. |
| `details` | array | Yes | Empty array for most errors. Populated with field-level validation failures for 400 errors (see §3.3). |

### 3.2 Error Code Registry

Error codes are `SCREAMING_SNAKE_CASE` strings. The registry below is exhaustive for v1. New codes may be added (non-breaking); existing codes will not be renamed or removed within v1.

**Authentication and authorization errors (401, 403):**

| HTTP | `errorCode` | Meaning |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | No `Authorization` header on a protected endpoint. |
| 401 | `TOKEN_EXPIRED` | JWT `exp` claim is in the past. |
| 401 | `TOKEN_INVALID` | JWT signature verification failed or token is malformed. |
| 401 | `TOKEN_REVOKED` | JWT `jti` is present in `jwt_denylist`. |
| 401 | `INVALID_CREDENTIALS` | Email/password combination did not match. |
| 403 | `ACCESS_DENIED` | Authenticated user does not own the requested resource. |
| 403 | `ACCOUNT_INACTIVE` | User account has `is_active = false`. |

**Resource errors (404, 409):**

| HTTP | `errorCode` | Meaning |
|---|---|---|
| 404 | `RESOURCE_NOT_FOUND` | The requested resource does not exist or has been soft-deleted. |
| 409 | `EMAIL_ALREADY_REGISTERED` | Registration attempted with an email that already exists. |
| 409 | `DUPLICATE_RESOURCE` | A unique constraint was violated (other than email). |
| 409 | `ALREADY_CONTRIBUTED` | Interview log has already been contributed to the public pool. |

**Validation errors (400):**

| HTTP | `errorCode` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | One or more request fields failed Bean Validation. `details` array is populated. |
| 400 | `INVALID_STATUS_TRANSITION` | Application status transition is not permitted by the state machine. |
| 400 | `PROMPT_INJECTION_DETECTED` | User-supplied text triggered the injection guard. |
| 400 | `INVALID_TEMPLATE_ID` | The specified resume template does not exist in the template catalog. |

**Pipeline errors (422, 502, 503):**

| HTTP | `errorCode` | Meaning |
|---|---|---|
| 422 | `GROUNDING_FAILURE` | LLM response referenced content not present in the supplied experience entries. |
| 422 | `INSUFFICIENT_EXPERIENCE` | User has no experience entries; tailoring cannot proceed. |
| 502 | `LLM_SCHEMA_ERROR` | LLM returned a response that failed JSON schema validation after the retry budget was exhausted. |
| 502 | `LLM_API_ERROR` | The upstream LLM API returned a non-retryable error. |
| 503 | `LLM_UNAVAILABLE` | LLM API call timed out or the service is unreachable. |
| 503 | `RENDERING_TIMEOUT` | Tectonic subprocess exceeded the configured timeout. |
| 500 | `RENDERING_FAILED` | Tectonic exited with a non-zero status. |
| 503 | `EMBEDDING_UNAVAILABLE` | Embedding API call timed out or the service is unreachable. |

**Server errors (500):**

| HTTP | `errorCode` | Meaning |
|---|---|---|
| 500 | `INTERNAL_ERROR` | Unclassified server error. The `traceId` field links to the Sentry event. |

### 3.3 Validation Error Details

When `errorCode` is `VALIDATION_FAILED`, the `details` array contains one object per failing field:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed. See details for field-level errors.",
  "path": "/api/v1/profile/experience",
  "timestamp": "2025-01-15T10:30:00Z",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "details": [
    {
      "field": "startDate",
      "rejectedValue": "not-a-date",
      "message": "must be a valid ISO 8601 date (YYYY-MM-DD)"
    },
    {
      "field": "description",
      "rejectedValue": null,
      "message": "must not be blank"
    }
  ]
}
```

**Detail object fields:**

| Field | Type | Description |
|---|---|---|
| `field` | string | The request body field path, using dot notation for nested fields (e.g., `"address.city"`). |
| `rejectedValue` | any \| null | The value that was rejected. Omitted (null) if the field was absent. Truncated to 200 characters if a string. |
| `message` | string | Human-readable constraint description. |

### 3.4 Exception-to-HTTP Mapping

The following table is the authoritative mapping implemented in the global `@RestControllerAdvice`. Every application exception maps to exactly one HTTP status and one `errorCode`. Unmapped exceptions fall through to the `INTERNAL_ERROR` handler.

| Exception class | HTTP status | `errorCode` |
|---|---|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| `AuthException` (invalid credentials) | 401 | `INVALID_CREDENTIALS` |
| `JwtExpiredException` | 401 | `TOKEN_EXPIRED` |
| `JwtInvalidException` | 401 | `TOKEN_INVALID` |
| `JwtRevokedException` | 401 | `TOKEN_REVOKED` |
| `AccountInactiveException` | 403 | `ACCOUNT_INACTIVE` |
| `EmailAlreadyRegisteredException` | 409 | `EMAIL_ALREADY_REGISTERED` |
| `DuplicateResourceException` | 409 | `DUPLICATE_RESOURCE` |
| `AlreadyContributedException` | 409 | `ALREADY_CONTRIBUTED` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `InvalidStatusTransitionException` | 400 | `INVALID_STATUS_TRANSITION` |
| `PromptInjectionException` | 400 | `PROMPT_INJECTION_DETECTED` |
| `TemplateNotFoundException` | 400 | `INVALID_TEMPLATE_ID` |
| `InsufficientExperienceException` | 422 | `INSUFFICIENT_EXPERIENCE` |
| `GroundingException` | 422 | `GROUNDING_FAILURE` |
| `SchemaValidationException` | 502 | `LLM_SCHEMA_ERROR` |
| `LlmApiException` | 502 | `LLM_API_ERROR` |
| `LlmTimeoutException` | 503 | `LLM_UNAVAILABLE` |
| `EmbeddingUnavailableException` | 503 | `EMBEDDING_UNAVAILABLE` |
| `RenderingTimeoutException` | 503 | `RENDERING_TIMEOUT` |
| `RenderingFailedException` | 500 | `RENDERING_FAILED` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

### 3.5 Error Handling for Pipeline Endpoints

The tailoring and scoring endpoints involve multi-step pipelines where partial success is meaningful. The following rules govern partial-success behavior:

**Tailoring pipeline (`POST /api/v1/tailoring/tailor`):**
- If the LLM call succeeds but PDF rendering fails, the endpoint returns **503** with `RENDERING_TIMEOUT` or **500** with `RENDERING_FAILED`. The `ResumeVersion` row is committed to the database with `render_status = 'FAILED'` and the `json_payload` intact. The response body includes the `resumeVersionId` in the `details` array so the client can display a "retry PDF generation" option.
- If the LLM call fails, nothing is persisted and the appropriate 5xx error is returned.

```json
{
  "errorCode": "RENDERING_TIMEOUT",
  "message": "PDF generation timed out. Your resume content was saved and can be retried.",
  "path": "/api/v1/tailoring/tailor",
  "timestamp": "2025-01-15T10:30:00Z",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "details": [
    {
      "field": "resumeVersionId",
      "rejectedValue": null,
      "message": "Resume content saved with id 17. Retry PDF generation at GET /api/v1/tailoring/resumes/17/pdf"
    }
  ]
}
```

**Contribution pipeline (`POST /api/v1/tracker/.../contribute`):**
- If the anonymization threshold is not met, the endpoint returns **200** (not an error) with `status: "STAGED"`. This is a successful operation with a deferred outcome, not a failure.

### 3.6 Security-Sensitive Error Responses

The following rules prevent information leakage through error responses:

- **404 vs 403 for owned resources:** When a user requests a resource that exists but belongs to another user, the response is **403 `ACCESS_DENIED`**, not 404. This is intentional: returning 404 would allow enumeration attacks to determine whether a resource ID exists. The `message` field says "you do not have access to this resource" without confirming or denying existence.
- **Login failures:** `POST /api/v1/auth/login` always returns 401 `INVALID_CREDENTIALS` regardless of whether the email exists. The response time is constant (BCrypt comparison is always performed, even against a dummy hash, to prevent timing-based email enumeration).
- **Stack traces:** Stack traces are never included in error responses in any environment. They are written to the application log and forwarded to Sentry only.
- **Internal error messages:** For `INTERNAL_ERROR` responses, the `message` field contains only `"An unexpected error occurred."` plus the `traceId`. The actual exception message is logged server-side only.

---

## 4. Pagination Strategy

### 4.1 Mechanism

All list endpoints use **offset-based pagination** implemented via Spring Data's `Pageable` abstraction. Cursor-based pagination is not used in v1; the data volumes and access patterns (user-scoped lists, public intel browse) do not require it.

### 4.2 Request Parameters

Pagination parameters are passed as query parameters on GET requests. They are never part of the request body.

| Parameter | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `page` | integer | `0` | `>= 0` | Zero-based page index. |
| `size` | integer | `20` | `1–100` | Number of items per page. Requests above 100 are rejected with 400 `VALIDATION_FAILED`. |
| `sort` | string | endpoint-specific | See §4.4 | Sort field and direction, e.g., `createdAt,desc`. |

**Example request:**
```
GET /api/v1/tracker/applications?page=0&size=20&sort=createdAt,desc
```

### 4.3 Response Envelope

All paginated responses are wrapped in a consistent envelope. The data array is always under the `content` key.

```json
{
  "content": [ /* array of resource objects */ ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 47,
    "totalPages": 3
  }
}
```

**Envelope field definitions:**

| Field | Type | Description |
|---|---|---|
| `content` | array | The items for the current page. Empty array (not null) when there are no results. |
| `page.number` | integer | The current page index (zero-based), echoing the request parameter. |
| `page.size` | integer | The page size used for this response, echoing the request parameter. |
| `page.totalElements` | integer | Total number of items across all pages matching the current filter. |
| `page.totalPages` | integer | Total number of pages: `ceil(totalElements / size)`. |

The envelope does not include `first`, `last`, `previousPage`, or `nextPage` links. Navigation is the client's responsibility using `page.number`, `page.totalPages`, and the request parameters.

### 4.4 Sort Fields by Endpoint

Each paginated endpoint defines its own allowed sort fields and default sort order. Requests specifying an unsupported sort field are rejected with 400 `VALIDATION_FAILED`.

| Endpoint | Allowed sort fields | Default sort |
|---|---|---|
| `GET /api/v1/jobs` | `postedAt`, `createdAt`, `title`, `company` | `postedAt,desc` |
| `GET /api/v1/jobs/search` | `postedAt`, `createdAt`, `title`, `company` | `postedAt,desc` |
| `GET /api/v1/tailoring/resumes` | `createdAt` | `createdAt,desc` |
| `GET /api/v1/tracker/applications` | `createdAt`, `updatedAt`, `appliedAt`, `status`, `companyName` | `updatedAt,desc` |
| `GET /api/v1/intel/companies` | `companyName` | `companyName,asc` |

Sort direction is specified as a suffix: `fieldName,asc` or `fieldName,desc`. If the direction suffix is omitted, `asc` is assumed.

Multi-field sort is supported using repeated `sort` parameters:
```
GET /api/v1/tracker/applications?sort=status,asc&sort=updatedAt,desc
```

### 4.5 Empty Results

An empty result set is not an error. The response is 200 with an empty `content` array:

```json
{
  "content": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

### 4.6 Pagination on Search

`GET /api/v1/jobs/search` accepts pagination parameters alongside filter parameters. Filter parameters are:

| Parameter | Type | Description |
|---|---|---|
| `q` | string | Full-text keyword query against `title`, `company`, `full_text`. |
| `location` | string | Filter by location (partial match). |
| `remoteType` | string | One of `remote`, `hybrid`, `onsite`. |
| `employmentType` | string | One of `full_time`, `contract`, `part_time`. |
| `salaryMin` | integer | Minimum salary filter (inclusive). |
| `postedAfter` | string (ISO 8601 date) | Filter to postings created after this date. |

All filter parameters are optional and combinable. `totalElements` in the response reflects the filtered count.

---

## 5. Versioning Policy

### 5.1 URL Versioning

The API version is encoded in the URL path as the first path segment after the context root: `/api/v1/`. This is the only versioning mechanism. Header-based versioning (`Accept: application/vnd.careercopilot.v1+json`) is not used; it adds complexity without benefit for a single-consumer API.

The version segment is `v{major}` where `major` is a positive integer. There is no minor version in the URL.

### 5.2 What Constitutes a Breaking Change

The following changes require a new major version (i.e., `/api/v2/`):

| Change type | Example |
|---|---|
| Removing a response field | Removing `coverageScore` from `AtsGapReport` |
| Renaming a response field | Renaming `missing` to `missingSkills` |
| Changing a field's type | Changing `score` from `number` to `string` |
| Removing an endpoint | Removing `GET /api/v1/jobs/{id}` |
| Changing an endpoint's HTTP method | Changing scoring from POST to PUT |
| Changing a required request field to a different name | Renaming `jobPostingId` to `jobId` in `TailoringRequest` |
| Removing a valid enum value from a request field | Removing `ONSITE` from `round_type` |
| Changing a 200 response to a 4xx for previously valid input | Making a previously optional field required |

### 5.3 What Does Not Constitute a Breaking Change

The following changes are non-breaking and may be deployed to v1 without a version bump:

| Change type | Example |
|---|---|
| Adding a new optional response field | Adding `salaryRange` to `JobPostingResponse` |
| Adding a new endpoint | Adding `GET /api/v1/profile/export` |
| Adding a new optional request field | Adding an optional `tone` field to `CoverLetterRequest` |
| Adding a new valid enum value to a response field | Adding a new `status` value to `ApplicationResponse` |
| Changing an error message string | Rewording a `message` field |
| Adding a new `errorCode` | Adding `TEMPLATE_RENDER_QUEUED` |
| Reducing a rate limit (more permissive) | Increasing tailoring limit from 20 to 30/hour |
| Performance improvements with identical semantics | Faster PDF rendering |

Clients must be written to tolerate unknown fields in responses (i.e., use lenient JSON deserialization). The SPA is built with this assumption.

### 5.4 Deprecation Process

When a breaking change is required:

1. `/api/v2/` is deployed alongside `/api/v1/`. Both versions are live simultaneously.
2. A `Deprecation` response header is added to all v1 responses: `Deprecation: true`.
3. A `Sunset` response header announces the removal date: `Sunset: Sat, 01 Jan 2026 00:00:00 GMT`.
4. The deprecation date is announced in the project changelog with a minimum 90-day notice period.
5. After the sunset date, v1 endpoints return 410 Gone with `errorCode: API_VERSION_SUNSET`.

In v1 (the current version), no endpoints are deprecated.

### 5.5 Experimental Endpoints

Endpoints under `/api/v1/internal/` or marked with the response header `X-Api-Status: experimental` carry no stability guarantee and may change or be removed without notice. No such endpoints exist in the current specification; this policy is documented for future use.

### 5.6 OpenAPI Document Versioning

The OpenAPI document served at `/api/v1/docs` carries its own `info.version` field, which follows semantic versioning (`1.0.0`, `1.1.0`, etc.) independently of the URL version. The URL version (`v1`) changes only on breaking changes; the OpenAPI document version increments on every change including additive ones. This allows clients to detect when new optional fields have been added without a URL version change.

---

## 6. OpenAPI Specification

```yaml
openapi: "3.1.0"

info:
  title: Career Copilot API
  version: "1.0.0"
  description: |
    REST API for the Career Copilot application. All endpoints are under `/api/v1/`.

    **Authentication:** Bearer JWT. Obtain a token via `POST /api/v1/auth/login`
    or `POST /api/v1/auth/register`. Include it as `Authorization: Bearer <token>`
    on all authenticated requests.

    **Errors:** All non-2xx responses return an `ErrorResponse` object.
    Branch on `errorCode`, not on `message`.

    **Pagination:** List endpoints accept `page`, `size`, and `sort` query parameters
    and return a `Page` wrapper object.
  contact:
    name: Career Copilot Engineering
  license:
    name: Proprietary

servers:
  - url: https://api.careercopilot.example.com/api/v1
    description: Production
  - url: http://localhost:8080/api/v1
    description: Local development

tags:
  - name: auth
    description: Registration, login, and token management
  - name: jobs
    description: Job posting ingestion, search, and manual paste
  - name: profile
    description: Candidate profile and