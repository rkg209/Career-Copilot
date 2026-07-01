# Skill: ATS Scoring

## Algorithm Overview

The ATS scorer produces a structured score report without requiring an LLM for the core scoring path. The LLM is only used for semantic augmentation (near-miss detection). This keeps scoring fast, cheap, and deterministic.

```
JobDescription + ResumeData
  → KeywordExtractor (deterministic NLP)
  → CoverageScorer (exact + normalized matching)
  → SemanticAugmenter (near-miss via embeddings, optional)
  → FixGenerator (template-based recommendations)
  → ScoringResult
```

---

## Output Schema

`ScoringResult` JSON (also the API response shape):

```json
{
  "score": 78,
  "breakdown": {
    "requiredSkillsCoverage": 0.85,
    "preferredSkillsCoverage": 0.60,
    "keywordDensity": 0.72,
    "titleMatch": true
  },
  "missingRequired": [
    { "keyword": "Kubernetes", "frequency": 3, "context": "required in JD line 12" }
  ],
  "missingPreferred": [
    { "keyword": "Helm", "frequency": 1 }
  ],
  "nearMisses": [
    {
      "resumeToken": "container orchestration",
      "jdKeyword": "Kubernetes",
      "similarity": 0.81,
      "suggestion": "Add 'Kubernetes' explicitly to skills or experience bullets."
    }
  ],
  "fixes": [
    {
      "type": "ADD_KEYWORD",
      "keyword": "Kubernetes",
      "suggestedPlacement": "skills section or experience bullet",
      "templateText": "Deployed microservices using Kubernetes and Helm on AWS EKS."
    }
  ]
}
```

---

## KeywordExtractor

**Class:** `com.careercopilot.scoring.KeywordExtractor`

**No LLM.** Uses deterministic NLP:
1. Tokenize JD text (split on whitespace, punctuation).
2. Lowercase, remove stop words (Apache Lucene stop list or custom).
3. Apply a tech-skills dictionary (curated list of known tech terms) for boosted extraction.
4. Use frequency threshold (appear >= 2 times OR appear in a skills/requirements section).
5. Tag each keyword as `REQUIRED` (from "required", "must have", "you will" sections) or `PREFERRED` (from "nice to have", "preferred", "bonus" sections).

```java
public record ExtractedKeyword(String keyword, Importance importance, int frequency, String context) {
    public enum Importance { REQUIRED, PREFERRED }
}

public List<ExtractedKeyword> extract(String jobDescriptionText);
```

---

## CoverageScorer

**Class:** `com.careercopilot.scoring.CoverageScorer`

Matches JD keywords against resume text using:
1. **Exact match**: lowercase comparison after normalization (singularize, remove punctuation).
2. **Normalized match**: apply synonym map (e.g., "JS" = "JavaScript", "k8s" = "Kubernetes", "ML" = "machine learning").
3. **Acronym expansion**: check both the acronym and full form.

```java
public CoverageResult score(List<ExtractedKeyword> jdKeywords, String resumeFullText);

public record CoverageResult(
    double requiredCoverage,       // 0.0-1.0
    double preferredCoverage,
    List<ExtractedKeyword> matched,
    List<ExtractedKeyword> missing
) {}
```

Scoring formula:
```
score = (requiredCoverage * 0.6 + preferredCoverage * 0.25 + keywordDensity * 0.15) * 100
```

---

## SemanticAugmenter

**Class:** `com.careercopilot.scoring.SemanticAugmenter`

Uses embeddings to find near-misses: resume tokens that are semantically similar to missing JD keywords but not exact matches.

- Embed each missing keyword using `EmbeddingModel`.
- Embed each resume sentence/phrase (chunked at sentence boundaries).
- Compute cosine similarity.
- If similarity > 0.75, flag as `nearMiss`.
- This is the ONLY part of scoring that uses the embedding model — no ChatClient/LLM.

```java
public List<NearMiss> findNearMisses(
    List<ExtractedKeyword> missingKeywords,
    List<String> resumeChunks
);
```

**Config:** Disable semantic augmentation in dev to save embedding API calls:
```yaml
careercopilot:
  scoring:
    semantic-augmentation-enabled: ${SCORING_SEMANTIC_ENABLED:true}
    near-miss-threshold: 0.75
```

---

## FixGenerator

**Class:** `com.careercopilot.scoring.FixGenerator`

Template-based recommendations. No LLM. Uses a fix template library keyed by keyword category (e.g., cloud platform, programming language, soft skill).

```java
public List<Fix> generateFixes(List<ExtractedKeyword> missing, ResumeData resume);

public record Fix(
    FixType type,
    String keyword,
    String suggestedPlacement,
    String templateText
) {
    public enum FixType { ADD_KEYWORD, STRENGTHEN_BULLET, ADD_SECTION, REORDER_SKILLS }
}
```

Fix logic:
- If missing keyword is a programming language already mentioned elsewhere → `STRENGTHEN_BULLET` (use it more explicitly).
- If missing keyword is a tool not mentioned at all → `ADD_KEYWORD` with placement suggestion.
- If missing keyword is a methodology (Agile, Scrum) → suggest adding to bullet text.

---

## ScoringService Orchestration

```java
// com.careercopilot.scoring.ScoringService
@Service
public class ScoringService {
    public ScoringResult score(String jobDescriptionText, ResumeData resumeData) {
        String resumeFullText = flattenResumeToText(resumeData);
        List<ExtractedKeyword> jdKeywords = keywordExtractor.extract(jobDescriptionText);
        CoverageResult coverage = coverageScorer.score(jdKeywords, resumeFullText);
        List<NearMiss> nearMisses = scoringConfig.isSemanticAugmentationEnabled()
            ? semanticAugmenter.findNearMisses(coverage.missing(), chunkResume(resumeData))
            : List.of();
        List<Fix> fixes = fixGenerator.generateFixes(coverage.missing(), resumeData);
        int score = calculateScore(coverage);
        return new ScoringResult(score, coverage, nearMisses, fixes);
    }
}
```

---

## Test Plan for Scoring

- `KeywordExtractorTest`: known JD text → assert expected keywords with correct importance tags.
- `CoverageScorerTest`: known resume + known keywords → assert coverage percentages.
- `SemanticAugmenterTest`: mock `EmbeddingModel`, assert near-miss detection at threshold.
- `FixGeneratorTest`: known missing keywords → assert correct fix type and template text.
- `ScoringIntegrationTest`: full pipeline with Testcontainers, mocked embedding for semantic step.

---

## Caching

Score results are cached by `(resumeId, jobPostingId)`. Cache is invalidated when:
- The resume is re-tailored (new version).
- The job posting is re-ingested with updated text.

Use Spring Cache (`@Cacheable`) with a short TTL (1 hour) since job postings can be updated.
