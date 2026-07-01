---
description: Implement a Career Copilot phase strictly against an approved spec file.
---

Read CLAUDE.md for all architecture rules, LLM rules, guardrail rules, privacy rules, and testing rules.
Read `.claude/skills/spec-author.md` for what a complete spec looks like.

Spec file to implement: $ARGUMENTS

Steps:

1. Read the spec file at the path provided in $ARGUMENTS.

2. Check the spec's Status field.
   - If Status is DRAFT: STOP. Print:
     "This spec is still in DRAFT status. Review and complete all sections, then change Status to APPROVED before implementing.
     Blocked sections that need completion: [list any sections with TODO or incomplete content]"
   - Only proceed if Status is APPROVED.

3. Read the full spec carefully. Identify all deliverables:
   - New database tables (from "Data Model Changes")
   - New API endpoints (from "API Changes")
   - New service classes and repository classes (from "Service & Component Design")
   - LLM tasks (from "LLM Tasks" section)
   - Tests required (from "Test Plan")

4. Implement feature by feature in this order:
   a. Flyway migrations first (run /migration for each new table)
   b. JPA entities
   c. Repository interfaces
   d. Service classes (with mocked dependencies initially)
   e. DTOs
   f. Controllers
   g. LLM tasks if any (use /add-llm-task)
   h. Tests

5. For each feature slice, use /new-feature to scaffold the package structure.

6. Pause and ask for clarification if:
   - The spec says something ambiguous (e.g., "handle errors appropriately" without specifying which errors)
   - A required piece of information is missing (e.g., no JSON schema for an endpoint)
   - There is a conflict between the spec and CLAUDE.md rules

   Do NOT guess — ask.

7. After all implementation is done, prompt the user to run /test to verify.

8. After tests pass, prompt the user to run /review to check against CLAUDE.md rules.

9. Remind the user: "Update the spec Status to IMPLEMENTED and update CLAUDE.md current phase when ready to deploy."
