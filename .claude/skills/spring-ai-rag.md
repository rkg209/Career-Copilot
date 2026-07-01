# Skill: Spring AI RAG Conventions

## Overview

This project uses Spring AI 1.1.x with pgvector for retrieval-augmented generation. All LLM calls go through `LlmGateway`. This skill documents the embedding strategy, retrieval configuration, tiered model config, and advisor wiring.

---

## Tiered Model Config Pattern

### LlmTaskConfig in application.yml

```yaml
careercopilot:
  llm:
    tiers:
      extraction:
        provider: openrouter          # or ollama in dev
        model: mistralai/mistral-7b-instruct
        max-input-tokens: 4096
        max-output-tokens: 512
        temperature: 0.1
      tailoring:
        provider: openrouter
        model: anthropic/claude-3.5-haiku
        max-input-tokens: 8192
        max-output-tokens: 2048
        temperature: 0.3
      scoring:
        provider: openrouter
        model: mistralai/mistral-7b-instruct
        max-input-tokens: 4096
        max-output-tokens: 1024
        temperature: 0.0
```

### LlmTaskConfig Java class

```java
// com.careercopilot.shared.llm.LlmTaskConfig
@ConfigurationProperties("careercopilot.llm.tiers")
public record LlmTaskConfig(
    TierConfig extraction,
    TierConfig tailoring,
    TierConfig scoring
) {
    public record TierConfig(
        String provider,
        String model,
        int maxInputTokens,
        int maxOutputTokens,
        double temperature
    ) {}
}
```

### LlmTask Enum

```java
// com.careercopilot.shared.llm.LlmTask
public enum LlmTask {
    EXTRACT_JD_SKILLS(Tier.EXTRACTION),
    EXTRACT_EXPERIENCE_KEYWORDS(Tier.EXTRACTION),
    TAILOR_RESUME(Tier.TAILORING),
    SCORE_RESUME(Tier.SCORING),
    GENERATE_INTERVIEW_QUESTIONS(Tier.TAILORING);

    public final Tier tier;
    LlmTask(Tier tier) { this.tier = tier; }

    public enum Tier { EXTRACTION, TAILORING, SCORING }
}
```

---

## Embedding Experience into pgvector

### Schema

```sql
CREATE TABLE experience_embeddings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id),
    experience_id UUID NOT NULL REFERENCES experience_entries(id),
    chunk_text   TEXT NOT NULL,
    embedding    vector(1536),      -- dimension matches embedding model
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX experience_embeddings_user_idx ON experience_embeddings(user_id);
CREATE INDEX experience_embeddings_ivfflat_idx
    ON experience_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

### Embedding Service

```java
// com.careercopilot.profile.EmbeddingService
@Service
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final ExperienceEmbeddingRepository repo;

    public void embedExperience(UUID userId, ExperienceEntry entry) {
        List<String> chunks = chunkExperience(entry);  // split bullets into chunks
        for (String chunk : chunks) {
            float[] vector = embeddingModel.embed(chunk);
            repo.save(new ExperienceEmbedding(userId, entry.getId(), chunk, vector));
        }
    }

    private List<String> chunkExperience(ExperienceEntry e) {
        // Each bullet becomes a chunk; also create a header chunk: "title at company"
        List<String> chunks = new ArrayList<>();
        chunks.add(e.getTitle() + " at " + e.getCompany() + ": " + e.getSummary());
        chunks.addAll(e.getBullets());
        return chunks;
    }
}
```

### Embedding model config

```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-3-small   # 1536 dims, cheap
```

---

## Retrieval via Spring AI VectorStore

### VectorStore bean

```java
@Bean
public VectorStore experienceVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .tableName("experience_embeddings")
        .dimensions(1536)
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .build();
}
```

### Retrieval with user isolation

```java
// Always filter by userId — never retrieve across users
SearchRequest request = SearchRequest.query(jobDescription)
    .withTopK(10)
    .withSimilarityThreshold(0.72)
    .withFilterExpression("user_id == '" + userId + "'");

List<Document> relevantChunks = vectorStore.similaritySearch(request);
```

---

## LlmGateway — Wiring RAG + Advisors

```java
// com.careercopilot.shared.llm.LlmGateway
@Component
public class LlmGateway {
    private final ChatClient.Builder chatClientBuilder;
    private final LlmTaskConfig taskConfig;
    private final PiiRedactionAdvisor piiAdvisor;
    private final PromptInjectionAdvisor injectionAdvisor;
    private final VectorStore vectorStore;

    public String tailorResume(UUID userId, String jobDescription, String systemPrompt) {
        TierConfig tier = taskConfig.tailoring();

        ChatClient client = chatClientBuilder
            .defaultAdvisors(
                piiAdvisor,
                injectionAdvisor,
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.query(jobDescription)
                        .withTopK(10)
                        .withFilterExpression("user_id == '" + userId + "'"))
                    .build()
            )
            .build();

        ChatResponse response = client.prompt()
            .system(systemPrompt)
            .user(jobDescription)
            .options(OpenAiChatOptions.builder()
                .model(tier.model())
                .maxTokens(tier.maxOutputTokens())
                .temperature(tier.temperature())
                .build())
            .call()
            .chatResponse();

        logTokenUsage(LlmTask.TAILOR_RESUME, response.getMetadata().getUsage());
        return response.getResult().getOutput().getContent();
    }

    private void logTokenUsage(LlmTask task, Usage usage) {
        log.debug("LLM [{}] input={} output={} model={}",
            task, usage.getPromptTokens(), usage.getGenerationTokens(),
            usage.getMetadata().getOrDefault("model", "unknown"));
    }
}
```

---

## Advisor Wiring Cheat Sheet

| Advisor | When to include | Purpose |
|---|---|---|
| `PiiRedactionAdvisor` | ALL calls | Strip PII before sending to LLM and from response |
| `PromptInjectionAdvisor` | When user-supplied text (JD) is in the prompt | Block injection attempts |
| `QuestionAnswerAdvisor` | RAG calls (tailoring) | Inject retrieved experience chunks |
| `MessageChatMemoryAdvisor` | Multi-turn sessions (interview trainer) | Maintain conversation history |

---

## Token Logging

Every `LlmGateway` method must call `logTokenUsage(task, usage)`. The log line must include:
- Task name (enum)
- Input token count
- Output token count
- Actual model used

In production, aggregate these logs into a cost dashboard (Phase 8).

---

## Dev vs Prod Config

```yaml
# application-dev.yml
spring:
  ai:
    openai:
      base-url: http://localhost:11434/v1   # Ollama
      api-key: ollama
careercopilot:
  llm:
    tiers:
      extraction:
        model: llama3.2
      tailoring:
        model: llama3.2
      scoring:
        model: llama3.2

# application-prod.yml
spring:
  ai:
    openai:
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY}
```

---

## Test Mocking Pattern

```java
@SpringBootTest
class TailoringIntegrationTest {
    @MockBean
    LlmGateway llmGateway;

    @Test
    void tailorResume_returnsValidJson() {
        when(llmGateway.tailorResume(any(), any(), any()))
            .thenReturn(loadFixture("sample-resume-data.json"));
        // ... rest of test
    }
}
```

Never use `@MockBean ChatClient` directly — always mock at the `LlmGateway` level.
