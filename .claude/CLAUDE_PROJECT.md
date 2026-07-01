# Career Copilot — Sub-Agent Project Brief

## What This Project Is

Career Copilot is a deployed, multi-user web application that automates job search end-to-end. Users enter their real experience once; the system tailors resumes for specific job postings using RAG, renders them to PDF via LaTeX templates, scores them against ATS criteria, and tracks applications and interviews. Anonymized interview intel is pooled into a shared public dataset.

## Repository Location

`/Users/rahul/Placement/Project/1_Career_Copilot`

## Current Phase

Phase 0 — Foundation & Walking Skeleton

## Stack Summary

- **Backend**: Java 21, Spring Boot 3.5, Spring AI 1.1.x, Spring Security (JWT), Spring Data JPA, Flyway
- **Database**: PostgreSQL 16 + pgvector (single DB for relational + vector)
- **LLM**: Spring AI ChatClient, OpenAI-compatible (OpenRouter prod, Ollama dev), config-driven
- **Resume rendering**: LaTeX templates + Tectonic (LLM emits JSON only, NEVER LaTeX)
- **Frontend**: React + TypeScript
- **Hosting**: DigitalOcean App Platform + Managed Postgres

## Package Structure

```
com.careercopilot.{auth,jobs,profile,tailoring,scoring,tracker,intel,shared}
```

## The 5 Rules Every Sub-Agent Must Follow

1. **LLM calls only via LlmGateway** — never use ChatClient directly, never hardcode model names.
2. **LLM emits JSON only** — never LaTeX, never plain text for resume content.
3. **All guardrails wired** — PiiRedactionAdvisor on all LLM calls; PromptInjectionAdvisor when user-supplied text is in the prompt; GroundingValidator on resume tailoring output.
4. **Per-user data isolation** — every DB query on user data must filter by userId. Only `company_interview_intel` is shared, and only after anonymization.
5. **Tests use Testcontainers + mocked LLM** — no H2, no real LLM API calls in tests.

## Key Reference Files

- `CLAUDE.md` — full project rules (read this first)
- `.claude/skills/` — domain skills (latex-resume-renderer, spring-ai-rag, ats-scoring, guardrails, anonymization, jobsource-adapter, spec-author)
- `specs/` — phase specs
- `.claude/commands/` — slash commands (/spec, /implement-spec, /new-feature, /add-llm-task, /migration, /test, /review, /deploy-checkpoint)
