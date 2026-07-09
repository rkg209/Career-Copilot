# Spec: Profile Expansion — Full Resume Corpus

## Header

| Field | Value |
|---|---|
| Phase | 2E — Profile Expansion (extends Phase 2 Candidate Profile & Experience Corpus) |
| Status | APPROVED |
| Version | 1.0 |
| Author | Rahul + Claude |
| Last Updated | 2026-07-08 |

---

## Goal

The current profile captures only basic contact info and work experience, but analysis of 50
shortlisted-student resumes (`Temp_resumes/`, IIT Bombay placement format) shows real resumes are
built from far more: education history (50/50), scholastic achievements (42/50), extracurriculars
(38/50), positions of responsibility (36/50), coursework (32/50), guided/self projects (~48/50),
thesis work (28/50), categorized technical skills (~25/50), certifications (13/50), and
publications (10/50). For students especially, projects — not jobs — are the backbone of the
resume. This phase expands the profile so users can capture **all** of this content, feeds the
new evidence types into the existing RAG retrieval corpus, and extends the resume JSON schema +
LaTeX templates so tailored resumes actually render these sections. Outcome: a student with no
work experience can still generate a complete, dense, ATS-ready resume from their profile.

---

## Scope

### In Scope

1. **Typed corpus entries** — generalize `experience_entries` with an `entry_type`
   (WORK, INTERNSHIP, PROJECT, POSITION_OF_RESPONSIBILITY, PUBLICATION) plus type-specific
   optional fields (`project_type`, `guide`, `context`, `url`). Existing embedding pipeline
   (`ExperienceEmbeddingService`, hash-based re-embed, `findTopKByVector`) reused unchanged.
2. **Education entries** — new `education_entries` table + owner-scoped CRUD (degree, institution,
   university/board, specialization, years, score as CPI/percentage/grade, ordering).
3. **Skill groups** — categorized skills ("Programming Languages: C, C++, Python") as a new
   `skill_groups` table + bulk-replace endpoint.
4. **Profile highlights** — one table for year-tagged bullet sections with a `highlight_type`:
   SCHOLASTIC_ACHIEVEMENT, CERTIFICATION, EXTRACURRICULAR. CRUD endpoints.
5. **Simple profile lists** — `coursework text[]` and `areas_of_interest text[]` columns added to
   `candidate_profiles`, edited via the existing `PUT /api/v1/profile`.
6. **Tailoring integration**:
   - Retrieval corpus automatically includes the new entry types (same table, same vector query).
   - Resume LLM output schema gains optional `projects`, `positionsOfResponsibility` arrays
     (grounded via `sourceEntryId`, same as experience).
   - Education, skill groups, scholastic achievements, certifications, coursework are injected
     **deterministically after the LLM call** (same pattern as contactInfo — never round-trip
     through the provider).
   - All 4 resume LaTeX templates extended with optional sections: Education (table-style),
     Projects, Positions of Responsibility, Achievements, Certifications, Coursework.
   - Template preview thumbnails regenerated with the richer dummy payload.
7. **React profile page overhaul** — section editors for education, projects/typed entries,
   skill groups, highlights, coursework, areas of interest.

### Out of Scope

- Resume PDF import / auto-parsing of an uploaded resume into profile sections (future phase).
- Gender / date-of-birth / photo fields (present in the sample resumes' institute header but
  deliberately excluded: discrimination-sensitive PII with no ATS value).
- Institute-branded header layout (logo, roll number, CPI table exactly as IIT format) — our
  templates keep their own neutral styles.
- Grade-per-course capture (coursework is a plain list; per-course grades appear in <10% of
  samples).
- A dedicated `publications` renderer section (publications are captured as corpus entries and can
  be selected by the LLM into project bullets; full citation-formatted section is a follow-up).
- Changes to the ATS scorer (it reads resume JSON text and is unaffected structurally).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-2E.1 | User can add corpus entries typed as WORK, INTERNSHIP, PROJECT, POSITION_OF_RESPONSIBILITY, or PUBLICATION, with optional guide, context (course/club/competition), project type, and URL |
| FR-2E.2 | User can CRUD education entries with degree, institution, university/board, specialization, start/end year, and a typed score (CPI, percentage, or grade) |
| FR-2E.3 | User can define ordered skill groups (category name + ordered skill list) replacing the flat skill display |
| FR-2E.4 | User can CRUD year-tagged highlights typed as SCHOLASTIC_ACHIEVEMENT, CERTIFICATION, or EXTRACURRICULAR |
| FR-2E.5 | User can maintain coursework and areas-of-interest lists on the profile |
| FR-2E.6 | All new corpus entry types are embedded and retrievable by the existing top-K vector query |
| FR-2E.7 | Tailored resume JSON includes LLM-selected, grounded `projects` and `positionsOfResponsibility` arrays; every item's `sourceEntryId` must exist in the user's corpus (422 otherwise) |
| FR-2E.8 | Education, skill groups, achievements, certifications, and coursework are injected into resume JSON deterministically post-LLM and rendered by all 4 resume templates |
| FR-2E.9 | Existing profiles and experience entries continue to work unchanged (entry_type defaults to WORK; all new fields nullable/optional) |
| NFR-2E.1 | All migrations additive; no breaking change to existing API request/response shapes |
| NFR-2E.2 | New entry types reuse the embedding pipeline without new LLM/embedding code paths |
| NFR-2E.3 | Per-user isolation: every new repository query filters by `user_id` |

---

## Data Model Changes

### V20 — typed corpus entries

```sql
-- V20__experience_entry_types.sql
CREATE TYPE corpus_entry_type AS ENUM
  ('WORK', 'INTERNSHIP', 'PROJECT', 'POSITION_OF_RESPONSIBILITY', 'PUBLICATION');

CREATE TYPE project_type AS ENUM ('COURSE', 'SELF', 'RESEARCH', 'THESIS');

ALTER TABLE experience_entries
  ADD COLUMN entry_type corpus_entry_type NOT NULL DEFAULT 'WORK',
  ADD COLUMN project_type project_type,          -- only for entry_type = PROJECT
  ADD COLUMN guide TEXT,                          -- e.g. "Prof. Leena Vachhani"
  ADD COLUMN context TEXT,                        -- course code, club, competition, venue
  ADD COLUMN url TEXT;                            -- repo / DOI / demo link

CREATE INDEX idx_experience_entries_user_type ON experience_entries (user_id, entry_type);
```

Semantics note: for PROJECT entries, `role` holds the project title and `company` holds the
organization ("Self Project", "IITB Mars Rover Team", course name). Mapper handles labeling.

### V21 — education entries

```sql
-- V21__education_entries.sql
CREATE TYPE education_score_type AS ENUM ('CPI', 'PERCENTAGE', 'GRADE');

CREATE TABLE education_entries (
    id             BIGSERIAL PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    degree         TEXT NOT NULL,             -- "B.Tech.", "M.Tech.", "Class XII"
    institution    TEXT NOT NULL,             -- "IIT Bombay", "SMP School"
    university     TEXT,                      -- board/university if distinct: "CBSE"
    specialization TEXT,                      -- "Aerospace Engineering", "Physics"
    start_year     SMALLINT,
    end_year       SMALLINT,                  -- expected or actual graduation year
    score_type     education_score_type,
    score_value    TEXT,                      -- "9.24", "88.40", "A"
    display_order  INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_education_entries_user ON education_entries (user_id, display_order);
```

### V22 — skill groups

```sql
-- V22__skill_groups.sql
CREATE TABLE skill_groups (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          TEXT NOT NULL,              -- "Programming Languages"
    skills        TEXT[] NOT NULL DEFAULT '{}',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE INDEX idx_skill_groups_user ON skill_groups (user_id, display_order);
```

### V23 — profile highlights

```sql
-- V23__profile_highlights.sql
CREATE TYPE highlight_type AS ENUM
  ('SCHOLASTIC_ACHIEVEMENT', 'CERTIFICATION', 'EXTRACURRICULAR');

CREATE TABLE profile_highlights (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          highlight_type NOT NULL,
    text          TEXT NOT NULL,              -- the bullet content
    year_label    TEXT,                       -- "2024", "2022-23"
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_profile_highlights_user ON profile_highlights (user_id, type, display_order);
```

### V24 — profile list columns

```sql
-- V24__profile_coursework_interests.sql
ALTER TABLE candidate_profiles
  ADD COLUMN coursework TEXT[] NOT NULL DEFAULT '{}',
  ADD COLUMN areas_of_interest TEXT[] NOT NULL DEFAULT '{}';
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V20 | `V20__experience_entry_types.sql` | Typed corpus entries + project fields |
| V21 | `V21__education_entries.sql` | Education table |
| V22 | `V22__skill_groups.sql` | Categorized skills |
| V23 | `V23__profile_highlights.sql` | Achievements / certifications / extracurriculars |
| V24 | `V24__profile_coursework_interests.sql` | Coursework + interests arrays |

All additive; existing rows default `entry_type = 'WORK'`. Enums use the project's native-enum +
`@JdbcTypeCode(SqlTypes.NAMED_ENUM)` convention. Year columns are SMALLINT mapped to `Short`
(per the SMALLINT/Integer ddl-validate gotcha) — or INTEGER mapped to Integer; pick INTEGER to
match project precedent.

---

## API Changes

All endpoints require JWT; all are owner-scoped. Error cases common to all:
`400` validation, `401` unauthenticated, `404` not found or not owned (per project convention).

### Modified: `GET/PUT /api/v1/profile`

`ProfileDto` gains two fields (backward compatible — old clients simply omit them):

```json
{
  "fullName": "string", "phone": "string", "headline": "string", "summary": "string",
  "location": "string", "linkedinUrl": "string", "githubUrl": "string", "portfolioUrl": "string",
  "targetRoles": ["string"], "targetLocations": ["string"], "openToRemote": true,
  "coursework": ["Deep Learning", "Operating Systems"],
  "areasOfInterest": ["Distributed Systems", "Geo-Spatial AI"]
}
```

### Modified: `GET/POST /api/v1/profile/experience`, `PUT/DELETE /api/v1/profile/experience/{id}`

`ExperienceRequest`/`ExperienceResponse` gain optional typed fields. `entryType` defaults to
`WORK` when omitted (backward compatible).

**Request:**
```json
{
  "role": "Mid-flight motor failure detection for quadcopter",
  "company": "Inter-IIT Tech Meet 2024",
  "location": "IIT Bombay",
  "startDate": "2024-10-01",
  "endDate": "2024-12-01",
  "current": false,
  "description": "High Preparation Problem Statement by IdeaForge",
  "achievements": ["Won Silver by developing a PA+NDI-based control law ..."],
  "skillsUsed": ["Simulink", "ROS"],
  "entryType": "PROJECT",
  "projectType": "COURSE",
  "guide": "Prof. Leena Vachhani",
  "context": "Inter-IIT Technical Meet 2024",
  "url": "https://github.com/user/quadcopter-failsafe"
}
```

**Response (200/201):** request shape + `id`, `embeddingStatus`, timestamps (existing shape plus
the new fields).

**Error cases:** `400` — `projectType` present when `entryType != PROJECT`; unknown enum values.

### New: `GET /api/v1/profile/education` → 200

```json
[
  {
    "id": 1, "degree": "B.Tech.", "institution": "IIT Bombay", "university": "IIT Bombay",
    "specialization": "Aerospace Engineering", "startYear": 2022, "endYear": 2026,
    "scoreType": "CPI", "scoreValue": "9.24", "displayOrder": 0
  }
]
```

### New: `POST /api/v1/profile/education` → 201

**Request:** same shape minus `id`. `degree` and `institution` required (`@NotBlank`);
`scoreType` required iff `scoreValue` present.

### New: `PUT /api/v1/profile/education/{id}` → 200, `DELETE .../{id}` → 204

### New: `GET /api/v1/profile/skill-groups` → 200

```json
[
  { "id": 1, "name": "Programming Languages", "skills": ["C++", "Python", "Java"], "displayOrder": 0 },
  { "id": 2, "name": "Libraries", "skills": ["PyTorch", "NumPy", "OpenCV"], "displayOrder": 1 }
]
```

### New: `PUT /api/v1/profile/skill-groups` → 200 (bulk replace)

**Request:** the array above without `id`s. Server replaces the user's full set atomically
(delete + insert in one transaction). Duplicate names within the payload → `400`.

### New: `GET /api/v1/profile/highlights?type=SCHOLASTIC_ACHIEVEMENT` → 200

`type` query param optional; omitted = all types.

```json
[
  { "id": 7, "type": "SCHOLASTIC_ACHIEVEMENT",
    "text": "97.44 %ile in JEE Advanced among 0.16M candidates", "yearLabel": "2022",
    "displayOrder": 0 }
]
```

### New: `POST /api/v1/profile/highlights` → 201, `PUT .../{id}` → 200, `DELETE .../{id}` → 204

**Request:** `{ "type": "CERTIFICATION", "text": "AWS Solutions Architect – Associate (Amazon)", "yearLabel": "2023", "displayOrder": 0 }`
— `type` and `text` required; `text` max 300 chars.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `ExperienceEntry` (modified) | `profile` | + entryType, projectType, guide, context, url |
| `EducationEntry`, `SkillGroup`, `ProfileHighlight` | `profile` | New JPA entities |
| `EducationRepository`, `SkillGroupRepository`, `ProfileHighlightRepository` | `profile` | Owner-scoped data access (`findByUserId...`) |
| `ProfileService` (modified) | `profile` | CRUD orchestration for all new sections; skill-group bulk replace transaction |
| `ProfileController` (modified) | `profile` | New endpoint mappings |
| `ExperienceText` (modified) | `profile` | Embedding text now includes entry type label, guide, context so retrieval distinguishes "course project under Prof. X" from "internship at Y" |
| `ResumeSectionAssembler` (new) | `tailoring` | Post-LLM deterministic injection: education, skillGroups, highlights, coursework into resume JSON (mirrors existing contactInfo injection) |
| `GroundingValidator` (modified) | `shared/guardrail` | Also walks `projects[].sourceEntryId` and `positionsOfResponsibility[].sourceEntryId` |
| `TailoringService` (modified) | `tailoring` | Retrieval unchanged; prompt asks for typed selection; calls assembler after validation |
| `ProfilePage.tsx` + section editor components | `frontend` | Education table editor, typed-entry form, skill-group editor, highlights lists |

### Interaction Flow (tailor, after this phase)

```
TailoringService.tailor(userId, jobId, templateId)
  → EmbeddingGateway.embed(JD)
  → ExperienceRepository.findTopKByVector(userId, k)     // now returns typed entries
  → LlmGateway(TAILORING) — JSON-only; entries labeled [PROJECT]/[WORK]/[POR] in prompt
  → JsonSchemaValidator (extended resume-data schema)
  → GroundingValidator (experience + projects + PORs)
  → ResumeSectionAssembler.inject(json, userId)          // education, skills, highlights, coursework
  → persist resume_versions → LatexRenderer.render(template, json)
```

---

## LLM Tasks

No new `LlmTask`. The existing TAILORING task is modified:

| Field | Value |
|---|---|
| Model Tier | TAILORING (unchanged) |
| LlmTask enum | `LlmTask.TAILORING` (unchanged) |
| Max tokens | unchanged |

**Prompt outline (delta):**
```
System: ...unchanged rules. Corpus entries are labeled with their type.
  Select and rewrite the most JD-relevant items into: experience[] (WORK/INTERNSHIP),
  projects[] (PROJECT/PUBLICATION), positionsOfResponsibility[] (POSITION_OF_RESPONSIBILITY).
  Each item must carry the sourceEntryId of its corpus entry. Emit 2-4 quantified,
  action-verb bullets per item. Do NOT emit education, skills, certifications — those are
  attached by the system.
User: [fenced JD] + [typed corpus entries with ids]
```

**Output JSON schema (delta to `resume-data` schema):**
```json
{
  "projects": {
    "type": "array",
    "items": {
      "type": "object",
      "required": ["sourceEntryId", "name", "bullets"],
      "properties": {
        "sourceEntryId": { "type": "integer" },
        "name": { "type": "string" },
        "context": { "type": "string" },
        "guide": { "type": "string" },
        "startDate": { "type": "string" },
        "endDate": { "type": "string" },
        "bullets": { "type": "array", "items": { "type": "string", "maxLength": 220 }, "minItems": 1, "maxItems": 4 }
      }
    }
  },
  "positionsOfResponsibility": { "same shape as projects, name = title, context = organization": true }
}
```
(`education`, `skillGroups`, `achievements`, `certifications`, `coursework` are appended by
`ResumeSectionAssembler` — never requested from or returned by the LLM.)

**Guardrails applied:**
- [x] PiiRedactionAdvisor (unchanged)
- [x] PromptInjectionAdvisor (JD text, unchanged)
- [x] GroundingValidator (extended to projects + PORs)
- [x] JsonSchemaValidator (extended schema)

---

## Rendering Tasks

All 4 resume templates gain optional Mustache sections (rendered only when data present):

| Template | New sections & placement |
|---|---|
| `classic-ats` | Education (after header), Projects (after Experience), Achievements, Positions of Responsibility, Certifications, Coursework (one line) |
| `modern-twocol` | Education (main column), Projects (main), POR (main), Achievements + Certifications + Coursework (sidebar), skill groups replace flat skills in sidebar |
| `compact` | Same sections, tighter spacing, coursework and skills as single lines |
| `academic` | Education first (most detailed), Projects labeled with guide ("Guide: Prof. X"), Publications-style formatting for PUBLICATION-sourced project items |

**JSON input schema for renderer (additions):**
```json
{
  "education": [
    { "degree": "B.Tech. (Aerospace Engineering)", "institution": "IIT Bombay",
      "yearRange": "2022 – 2026", "score": "CPI 9.24" }
  ],
  "skillGroups": [ { "name": "Programming Languages", "skillsJoined": "C++ · Python · Java" } ],
  "projects": [
    { "name": "Quadcopter motor-failure control", "context": "Inter-IIT Tech Meet 2024",
      "guide": "Prof. Leena Vachhani", "dateRange": "Oct 2024 – Dec 2024", "bullets": ["..."] }
  ],
  "positionsOfResponsibility": [
    { "name": "Team Lead", "context": "IITB Mars Rover Team", "dateRange": "May 2024 – Present", "bullets": ["..."] }
  ],
  "achievements": [ { "text": "97.44 %ile in JEE Advanced", "yearLabel": "2022" } ],
  "certifications": [ { "text": "AWS Solutions Architect – Associate", "yearLabel": "2023" } ],
  "courseworkJoined": "Deep Learning · Operating Systems · Convex Optimization"
}
```

Flat `skills`/`skillsJoined` remains for users with no skill groups (fallback). Preview
thumbnails in `frontend/public/template-previews/` are regenerated from a dummy payload
exercising every new section.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `ProfileServiceTest` (extended) | Education/highlight/skill-group CRUD logic, ownership checks, skill-group bulk replace, projectType-requires-PROJECT validation — mocked repositories |
| `ExperienceTextTest` (extended) | Embedding text includes type label, guide, context; hash changes when they change |
| `ResumeSectionAssemblerTest` | Deterministic injection of education/skills/highlights/coursework into resume JSON; empty-profile → sections omitted |
| `GroundingValidatorTest` (extended) | Unknown `sourceEntryId` in `projects`/`positionsOfResponsibility` → 422; valid ids pass |
| `TemplateRegistryTest` (unchanged) | still green |

### Integration Tests (Testcontainers, LLM mocked)

| Test class | What it tests |
|---|---|
| `ProfileSectionsIntegrationTest` | Full CRUD across education, skill-groups, highlights endpoints; per-user isolation (user A cannot read/modify user B's rows) |
| `TypedCorpusIntegrationTest` | POST PROJECT/POR entries → embedded (mock EmbeddingGateway) → `findTopKByVector` returns them; legacy rows read back as WORK |
| `TailoringIntegrationTest` (extended) | Mocked LLM returns projects+PORs → grounded → education/skills injected → resume JSON persisted with all sections |

### Guardrail Tests

| Test | Scenario |
|---|---|
| `GroundingValidatorTest` (above) | Invented project rejected |
| `TailoringSchemaValidationTest` (extended) | Malformed `projects` array triggers retry-once → 502 |

### Render Tests (real Tectonic, skipped if not installed)

| Test | Scenario |
|---|---|
| `LatexRendererTest` (extended payload) | All 4 templates compile with every new section populated AND with all new sections absent (legacy payload) — 8 renders, `%PDF-` magic asserted |

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` green locally with Docker (Testcontainers)
- [ ] `npx tsc --noEmit` + `npm run build` green in `frontend/`
- [ ] V20–V24 reviewed: additive only, enums created before use
- [ ] Template preview PNGs regenerated and committed
- [ ] No secrets committed

### Environment Variables Required

None new.

### DigitalOcean Deploy Steps

Standard: push to `main` → CI → auto-deploy → Flyway V20–V24 apply on startup → smoke test
`GET /actuator/health`, then `GET /api/v1/profile/education` with a JWT (expect `[]`), and one
tailor round-trip on a seeded profile. Stacks with pending Deploy Checkpoint 1.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] V20–V24 migrations apply cleanly on a fresh database and on an existing Phase-6 database
- [ ] All new/modified endpoints implemented per the JSON shapes above
- [ ] Typed entries retrievable via `findTopKByVector`; legacy entries unaffected (read back as WORK)
- [ ] `ProfileSectionsIntegrationTest`, `TypedCorpusIntegrationTest`, extended `TailoringIntegrationTest` pass
- [ ] Extended `GroundingValidatorTest` + schema-validation tests pass
- [ ] `LatexRendererTest` compiles all 4 templates with and without new sections
- [ ] Profile page renders and edits all new sections; tailor flow produces a PDF containing education + projects for a work-experience-free profile
- [ ] `/review` checklist passes
- [ ] CLAUDE.md + `progress_report.md` updated
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
