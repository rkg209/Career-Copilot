# Demo script — end to end

A walkthrough from an empty account to a tracked, scored application and a practiced interview
question, closing on the two metrics endpoints. Run this against a deployed instance (or locally
with `./mvnw spring-boot:run` + `npm run dev`) after `docs/rate-limiting.md`'s recommendations and
a fresh demo account.

## 0. Reset demo data

If reusing an account from a prior demo: either register a fresh email (fastest, no state to
clean up) or, if you need a specific email, delete its rows from `experience_entries`,
`resume_versions`, `scoring_results`, `applications`, `interview_logs`, and `mock_questions` /
`practice_answers` / `review_cards` before starting — `company_interview_intel` is shared and
should generally be left alone.

## 1. Register → job

1. Register a new account (`POST /api/v1/auth/register` or the `/register` page).
2. Browse `/jobs` — expect a page of live-ingested postings (RemoteOK/Greenhouse/Lever/etc.),
   filterable by search/location/salary.
3. Open a posting's detail page. **Expected:** full JD text, skills, "Tailor resume" and "Track
   this application" actions.

## 2. Profile

1. Go to `/profile`. Fill in contact info, a headline, and target roles.
2. Add at least 2–3 experience-corpus entries (work/internship/project) with real bullet content —
   the tailoring pipeline retrieves from these, so an empty corpus produces a thin resume.
3. **Expected:** each new entry shows `embeddingStatus: PENDING` briefly, then flips to
   `EMBEDDED` once the async embed completes (poll or refresh).

## 3. Tailor

1. From the job detail page, click "Tailor resume", pick a template.
2. **Expected:** a `ResumeVersion` is created, `renderStatus` moves `PENDING → RENDERED`, and a
   PDF download becomes available. If the corpus is too thin, tailoring returns a 422 with a
   grounding error rather than inventing experience — that's the guardrail working, not a bug.

## 4. Score

1. From `/resumes`, click "ATS score" on the tailored resume.
2. **Expected:** a `GapReport` — composite score 0–100, required/preferred coverage, missing
   keywords, near-misses (semantic), and suggested fixes.

## 5. Track

1. Back on the job detail page, click "Track this application".
2. On `/applications`, move the card through the status kanban (`SAVED → APPLIED → …`).
3. Open the application, log an interview round with a question and feedback.
4. Click "Contribute to community intel". **Expected:** `202`/staged the first two times for a
   given company, then `200 PUBLISHED` once 3 anonymized entries exist for it
   (`INTEL_MIN_AGGREGATION`) — the response reports `publishedCountForCompany` and `threshold`.

## 6. Company intel

1. Visit `/intel` (no login required). **Expected:** the company just published above appears,
   with round types, an average difficulty, and pooled questions — never a user identity.

## 7. Practice

1. Visit `/trainer`, generate a batch of questions (optionally scoped to the company from step 5).
2. Answer one. **Expected:** an LLM-graded 0–5 score, feedback, and a next-review date from the
   SM-2 scheduler.
3. Visit `/trainer/review` — due cards should be empty right after answering (next review is in
   the future) but will appear once the interval elapses.

## 8. Closing beat — the metrics

1. `curl -H "Authorization: Bearer $TOKEN" $API/api/v1/metrics/ats-improvement` — the headline
   number: labeled-set mean improvement (deterministic, no LLM) plus the live-user aggregate,
   which now includes this session's before/after delta if you scored the same job twice.
2. `curl -H "Authorization: Bearer $TOKEN" $API/api/v1/metrics/tokens` — per-task token/latency
   totals for this account, sourced from `token_usage_log`.
3. Open `$API/api/v1/docs/swagger-ui` to show the full API surface is documented and matches what
   was just exercised.
