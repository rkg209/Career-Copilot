---
description: Generate a detailed DRAFT technical spec for a Career Copilot phase using the spec template.
---

Read `.claude/skills/spec-author.md` to understand what makes an implementation-ready spec.
Read `specs/SPEC_TEMPLATE.md` for the required structure.

If no phase name was provided in $ARGUMENTS, ask: "Which phase do you want to spec? (e.g., phase-1-job-ingestion)"

Phase to spec: $ARGUMENTS

Steps:

1. Determine the output filename: `specs/{phase-name}.md` (use the argument as-is, lowercase with dashes).

2. Check if `specs/{phase-name}.md` already exists. If it does, ask: "A spec for this phase already exists. Overwrite, or append/update sections?"

3. Using `specs/SPEC_TEMPLATE.md` as the structure, generate a complete spec for the requested phase.

   Follow the 8-phase plan from CLAUDE.md:
   - Phase 0: Foundation & Walking Skeleton
   - Phase 1: Job Ingestion / Portal
   - Phase 2: Candidate Profile & Experience Corpus
   - Phase 3: Resume Tailoring (RAG + JSON + LaTeX)
   - Phase 4: ATS Gap Scorer
   - Phase 5: Application & Interview Tracker
   - Phase 6: Public Company-Intel Aggregation
   - Phase 7: Mock-Interview Trainer + Spaced Repetition
   - Phase 8: Hardening, Metrics & Polish

4. For the generated spec:
   - Status MUST be DRAFT
   - Version starts at 1.0
   - All required sections must be present (see spec-author.md for what "required" means)
   - API endpoints must have complete JSON request/response schemas
   - Data model changes must include actual SQL DDL
   - LLM tasks must specify model tier, prompt outline, output schema, and guardrails
   - Test plan must name specific test classes

5. Write the spec to `specs/{phase-name}.md`.

6. After writing, print:
   "Spec written to specs/{phase-name}.md with Status: DRAFT.
   Review all sections carefully. When ready for implementation, change Status to APPROVED.
   Then run: /implement-spec specs/{phase-name}.md"
