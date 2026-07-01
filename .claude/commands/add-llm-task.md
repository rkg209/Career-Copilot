---
description: Scaffold a new ChatClient-based LLM task with tiered model config, guardrail advisors, JSON schema validation, and token logging.
---

Read CLAUDE.md for all LLM rules (all calls via LlmGateway, no hardcoded models, tiered config, token logging).
Read `.claude/skills/spring-ai-rag.md` for LlmGateway wiring patterns and tiered config.
Read `.claude/skills/guardrails.md` for advisor wiring and JSON validation.

Task name: $ARGUMENTS

If no task name provided, ask:
1. "What is the task name? (e.g., 'ExtractJdSkills', 'TailorResume')"
2. "Which model tier? EXTRACTION (cheap, fast) / TAILORING (strong) / SCORING"
3. "Does this task take user-supplied or scraped text (job descriptions)? [y/n] — determines if PromptInjectionAdvisor is needed"
4. "Does this task generate resume content? [y/n] — determines if GroundingValidator is needed"
5. "What is the expected JSON output structure? (describe the fields)"

Steps:

1. Determine the LlmTask enum entry name (SCREAMING_SNAKE_CASE). Example: `TailorResume` → `TAILOR_RESUME`

2. Add the task to `com.careercopilot.shared.llm.LlmTask` enum:
   ```java
   {TASK_NAME}(Tier.{TIER}),
   ```

3. Create the JSON schema file at `src/main/resources/schemas/{task-kebab-case}-schema.json` based on the described output structure.

4. Register the schema in `LlmGateway` or the `JsonSchemaValidator` config:
   ```java
   jsonSchemaValidator.register(LlmTask.{TASK_NAME}, "schemas/{task-kebab-case}-schema.json");
   ```

5. Add a method to `com.careercopilot.shared.llm.LlmGateway`:

   ```java
   public String {taskMethodName}({params}) {
       TierConfig tier = taskConfig.{tier}();

       // Build advisor list based on task requirements
       List<RequestResponseAdvisor> advisors = new ArrayList<>();
       advisors.add(piiRedactionAdvisor);  // ALWAYS include
       // if user-supplied text: advisors.add(promptInjectionAdvisor);
       // if RAG: advisors.add(QuestionAnswerAdvisor.builder(vectorStore)...build());

       ChatClient client = chatClientBuilder
           .defaultAdvisors(advisors)
           .build();

       ChatResponse response = client.prompt()
           .system("""
               {system prompt — describe role and output format}
               Return ONLY valid JSON. No prose, no markdown fences.
               """)
           .user({user message with variables})
           .options(OpenAiChatOptions.builder()
               .model(tier.model())
               .maxTokens(tier.maxOutputTokens())
               .temperature(tier.temperature())
               .build())
           .call()
           .chatResponse();

       String rawJson = extractJson(response.getResult().getOutput().getContent());
       logTokenUsage(LlmTask.{TASK_NAME}, response.getMetadata().getUsage());

       // JSON validation (always)
       ValidationResult validation = jsonSchemaValidator.validate(rawJson, LlmTask.{TASK_NAME});
       if (!validation.isValid()) {
           log.warn("[{}] schema validation failed: {}", LlmTask.{TASK_NAME}, validation.getErrors());
           // retry once
           // ... retry logic
           if (!validation.isValid()) throw new LlmOutputException(LlmTask.{TASK_NAME}, validation.getErrors());
       }

       // if resume tailoring: GroundingValidator.validate(parsed, userCorpus)

       return rawJson;
   }
   ```

6. Create a corresponding DTO/record for the parsed output in the calling feature's `dto/` package.

7. Create a test stub in `src/test/java/com/careercopilot/shared/llm/{TaskName}LlmTaskTest.java`:
   - Mock `LlmGateway` to return a fixture JSON
   - Assert the calling service parses it correctly
   - Assert PII redaction is applied (verify `PiiRedactionAdvisor` is invoked)
   - If injection detection enabled: assert injected text is sanitized
   - Assert schema validation is called

8. Add a fixture file at `src/test/resources/fixtures/{task-kebab-case}-response.json` with a sample valid response.

9. Print summary:
   "LLM task '{TaskName}' scaffolded:
   - LlmTask.{TASK_NAME} added to enum (tier: {TIER})
   - Schema: src/main/resources/schemas/{task-kebab-case}-schema.json
   - Method: LlmGateway.{taskMethodName}()
   - Test stub: {TaskName}LlmTaskTest.java
   - Fixture: src/test/resources/fixtures/{task-kebab-case}-response.json

   Next: fill in the system/user prompts, complete the output DTO, add guardrails per checklist."
