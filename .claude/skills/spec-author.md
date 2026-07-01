# Skill: Spec Author

## What a Spec Is For

A spec is the contract between planning and implementation. It must be detailed enough that a developer (or Claude) can implement the phase without asking clarifying questions about what to build. It does NOT prescribe implementation details unnecessarily — it specifies behavior, schema, and interfaces.

---

## The Template to Use

Always use `specs/SPEC_TEMPLATE.md` as the starting structure. Do not skip sections; mark them "N/A for this phase" if genuinely not applicable (e.g., "No LLM tasks in Phase 0").

---

## Required Sections (All Phases)

Every spec must have all of these:

1. **Header** — phase number, name, status (starts DRAFT), version, date.
2. **Goal** — one tight paragraph. What does this phase accomplish? Why does it matter?
3. **Scope** — explicit in/out. "Out of scope" prevents scope creep.
4. **Requirements Satisfied** — link to requirement IDs (FR-N, NFR-N). If no formal requirements doc exists, define the IDs here.
5. **Data Model Changes** — actual SQL DDL for every new table/column. Flyway migration file names.
6. **API Changes** — every new endpoint with exact HTTP method, path, request/response JSON shapes, and all error cases.
7. **Service & Component Design** — key classes, their responsibilities, and the interaction flow (call chain). Not all methods — just the key ones.
8. **LLM Tasks** (or "N/A") — model tier, prompt outline, output schema, guardrails.
9. **Rendering Tasks** (or "N/A") — template used, JSON input schema.
10. **Test Plan** — unit tests, integration tests, guardrail tests (if LLM). Specific test class names.
11. **Deploy Steps** — pre-deploy checklist, env vars, DigitalOcean steps.
12. **Definition of Done** — checkboxes. Every box must be checkable by a human.

---

## How to Link to Requirement IDs

Use short IDs: `FR-{phase}.{number}` and `NFR-{phase}.{number}`.

Examples:
- `FR-2.1` — "User can add an experience entry with title, company, dates, and bullet points."
- `NFR-2.1` — "Experience embedding completes within 5 seconds per entry."

If the project has a separate requirements document, reference it. If not, define the IDs directly in the spec under "Requirements Satisfied."

---

## How to Write the API Changes Section

For every new endpoint, you must specify:

1. HTTP method + path
2. Auth requirement (public / requires JWT)
3. Request body JSON (with field names, types, constraints)
4. Success response JSON (with HTTP status code)
5. All error cases (status code + reason)

Do not say "standard CRUD." Write the actual schema. This is what gets implemented.

---

## How to Write the Test Plan

Specify:
- The test CLASS name (so the developer knows exactly what to create).
- What scenario each test covers (one sentence).
- Whether it's a unit test (mocked dependencies) or integration test (Testcontainers).
- For phases with LLM tasks: guardrail tests are mandatory.

Example:

```
| TailoringServiceTest | tailorResume() returns ValidResumeData with mocked LlmGateway |  Unit |
| TailoringIntegrationTest | Full tailor flow: user profile → LLM mock → PDF stored | Integration |
| TailoringGuardrailTest | GroundingValidator rejects invented experience | Unit |
```

---

## What "Implementation-Ready" Means (vs. Draft)

A spec is **DRAFT** when:
- Any required section is missing or marked TODO.
- API shapes are described in prose without JSON schemas.
- The test plan only says "write some tests."
- Data model has vague descriptions instead of DDL.

A spec is **APPROVED** (ready for implementation) when:
- Every required section is complete.
- Every API endpoint has a concrete JSON schema.
- Every new table has actual DDL.
- Test class names are listed.
- The DoD checklist is complete.
- A human has reviewed and explicitly set status to APPROVED.

**The `/implement-spec` command will refuse to proceed on a DRAFT spec.**

---

## Spec Workflow

```
/spec <phase-name>      → Creates specs/{phase-name}.md with Status: DRAFT
Review + edit manually  → Fill in all sections, make it implementation-ready
Set Status: APPROVED    → Only humans do this (or explicitly ask Claude to)
/implement-spec specs/{phase-name}.md  → Claude implements feature by feature
Mark Status: IMPLEMENTED after deploy
```

---

## Common Mistakes to Avoid

- **Vague scope**: "Add job features" — not acceptable. List each feature as a specific deliverable.
- **Missing error cases**: Every endpoint must document 400/401/403/404 handling.
- **LLM tasks without guardrails**: If a task includes user-supplied text (JD), it MUST list PromptInjectionAdvisor. If it generates resume content, it MUST list GroundingValidator.
- **No migration files**: Every schema change needs a migration file name, even if the file doesn't exist yet.
- **Ambiguous DoD**: "Tests pass" is too vague. "AuthIntegrationTest passes with Testcontainers" is correct.

---

## Spec Review Checklist (before approving)

- [ ] All required sections present and complete
- [ ] Every new endpoint has request/response JSON schemas
- [ ] Every new table has DDL and migration file name
- [ ] LLM tasks list model tier and required guardrails
- [ ] Test class names are explicit
- [ ] DoD checkboxes are specific and verifiable
- [ ] Scope section lists explicit out-of-scope items
- [ ] No "TBD" or "TODO" remaining in any section
