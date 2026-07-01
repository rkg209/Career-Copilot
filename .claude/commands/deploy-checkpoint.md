---
description: Run the pre-deploy checklist and emit DigitalOcean deploy steps for the current phase.
---

Read CLAUDE.md to identify the current phase and deploy rules.

Run the pre-deploy checklist sequentially. Stop and report if any step fails — do not continue to the next step until the issue is resolved.

---

## Step 1: Tests Green

Run:
```
./mvnw test 2>&1
```

- PASS: "Tests: {N} passed, 0 failed"
- FAIL: List failing tests. STOP. "Fix failing tests before deploying. Run /test for details."

---

## Step 2: Code Formatting (Spotless)

Run:
```
./mvnw spotless:check 2>&1
```

- PASS: No output or "BUILD SUCCESS"
- FAIL: "Spotless violations found. Run ./mvnw spotless:apply to auto-fix, then commit."

---

## Step 3: Flyway Migration Safety Check

List all files in `src/main/resources/db/migration/` and scan for destructive operations:

Search each migration file for:
- `DROP TABLE` — STOP if found (unless it's a controlled rollback migration with explicit approval)
- `DROP COLUMN` — STOP if found
- `ALTER TABLE ... ALTER COLUMN ... SET NOT NULL` without a default — STOP if found
- `TRUNCATE` — STOP if found
- `DELETE FROM` without `WHERE` — STOP if found

- PASS: "All {N} migrations are additive. Safe to apply."
- FAIL: "Destructive migration detected in {filename}: {line}. Review before deploying."

---

## Step 4: Secret Scan

Scan all staged/changed files for potential secrets:

Search for patterns:
- String literals containing `password`, `secret`, `api_key`, `apikey`, `private_key` assigned to variables in Java or yml files
- Lines matching `[A-Za-z0-9]{32,}` that look like API keys (heuristic)
- Any `.env` files tracked in git

Exclusions (safe):
- `${...}` Spring EL expressions (reading from env)
- `@Value("${...}")` annotations
- `application-test.yml` entries using Testcontainers

- PASS: "No hardcoded secrets found."
- FAIL: "Potential secret in {file}:{line} — move to environment variable before deploying."

---

## Step 5: Required Env Vars Documentation

Read the current phase's spec file (from CLAUDE.md current phase) and extract the "Environment Variables Required" table.

Print:
```
Required environment variables for this phase:
  {VAR_NAME} — {description}
  ...

Verify these are set in DigitalOcean App Platform before deploying.
```

---

## Step 6: Docker Build Check

Run:
```
docker build -t careercopilot:checkpoint . 2>&1
```

- PASS: "Docker build succeeded."
- FAIL: Print the first 20 lines of build error. "Fix Docker build before deploying."

---

## Deploy Steps (DigitalOcean)

Once all checklist items pass, print the deploy steps:

```
PRE-DEPLOY CHECKLIST: ALL PASSED

DEPLOY STEPS FOR {CURRENT PHASE}:

1. Commit and push all changes to the 'main' branch:
   git push origin main

2. GitHub Actions CI will run automatically. Wait for green.
   Monitor at: https://github.com/{repo}/actions

3. On green CI, DigitalOcean App Platform auto-deploys (if configured).
   OR trigger manually at: https://cloud.digitalocean.com/apps

4. Monitor startup logs in DigitalOcean for Flyway migration output:
   Look for: "Successfully applied N migration(s) to schema"
   Alert on: "Migration ... failed"

5. Smoke tests — run these against the production URL:
   curl https://{domain}/actuator/health
   → Expected: {"status":"UP"}

   curl -X POST https://{domain}/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","password":"testpass"}'
   → Expected: {"token":"..."}

6. If deploy fails:
   - Check DigitalOcean logs for startup errors
   - Flyway failures: check migration SQL syntax and ordering
   - Container crashes: check for missing env vars

7. After successful smoke tests:
   - Update CLAUDE.md "Current Phase" to the next phase
   - Mark the current phase spec Status as IMPLEMENTED
```
