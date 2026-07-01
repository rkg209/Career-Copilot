---
description: Run the Testcontainers test suite and report failures concisely. Check for LLM mock leaks.
---

Run the full test suite and report results.

Steps:

1. Run the tests:
   ```
   ./mvnw test -Dtest="**/*IntegrationTest*,**/*Test*" 2>&1
   ```
   (Run from the project root: /Users/rahul/Placement/Project/1_Career_Copilot)

2. Parse the output and report:

   **Summary line:**
   ```
   Tests: {total} | Passed: {passed} | Failed: {failed} | Errors: {errors} | Skipped: {skipped}
   ```

   **If all pass:**
   ```
   All tests passed.
   ```

   **If failures exist, list each:**
   ```
   FAILED: {TestClassName}.{methodName}
     Error: {exception type}: {message}
     At: {file:line}
   ```

3. Check for LLM mock leaks — scan test output for signs of real API calls:
   - Lines containing "openrouter.ai", "api.openai.com", or any external LLM URL
   - HTTP 200/201 responses from non-localhost URLs during tests
   - Connection timeout errors to LLM endpoints

   If any leaks detected:
   ```
   WARNING: Possible LLM mock leak detected in tests.
   Tests should never make real LLM API calls. Check:
   - Is LlmGateway mocked with @MockBean in all integration tests?
   - Is there a @TestConfiguration that replaces ChatClient with a stub?
   Suspected leaking tests: [list test classes that logged suspicious output]
   ```

4. Check Testcontainers startup — if tests failed with container errors:
   - "Could not find a valid Docker environment" → Docker not running
   - "Container startup failed" → look for the container logs in output

   Report:
   ```
   Testcontainers issue: {error}
   Fix: {suggested action}
   ```

5. Final status:
   - If tests green and no LLM leaks: "Ready to proceed. Run /review before deploying."
   - If failures: "Fix failing tests before deploying. Do not deploy on red."
