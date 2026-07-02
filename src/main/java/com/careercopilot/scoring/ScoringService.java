package com.careercopilot.scoring;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.scoring.dto.AtsGapReport;
import com.careercopilot.scoring.dto.ScoreRequest;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tailoring.ResumeVersion;
import com.careercopilot.tailoring.ResumeVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a score run: extract JD keywords (LLM, EXTRACTION tier) → deterministic coverage
 * math → embedding near-misses → template fixes → persist snapshot. The LLM never assigns the
 * score.
 */
@Service
public class ScoringService {

  private final UserRepository userRepository;
  private final ResumeVersionRepository resumeVersionRepository;
  private final JobPostingRepository jobPostingRepository;
  private final KeywordExtractor keywordExtractor;
  private final CoverageScorer coverageScorer;
  private final SemanticAugmenter semanticAugmenter;
  private final FixGenerator fixGenerator;
  private final ScoringResultRepository scoringResultRepository;
  private final ObjectMapper objectMapper;

  public ScoringService(
      UserRepository userRepository,
      ResumeVersionRepository resumeVersionRepository,
      JobPostingRepository jobPostingRepository,
      KeywordExtractor keywordExtractor,
      CoverageScorer coverageScorer,
      SemanticAugmenter semanticAugmenter,
      FixGenerator fixGenerator,
      ScoringResultRepository scoringResultRepository,
      ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.resumeVersionRepository = resumeVersionRepository;
    this.jobPostingRepository = jobPostingRepository;
    this.keywordExtractor = keywordExtractor;
    this.coverageScorer = coverageScorer;
    this.semanticAugmenter = semanticAugmenter;
    this.fixGenerator = fixGenerator;
    this.scoringResultRepository = scoringResultRepository;
    this.objectMapper = objectMapper;
  }

  public AtsGapReport score(String email, ScoreRequest request) {
    User user = resolveUser(email);
    ResumeVersion resume = ownedResume(user, request.resumeVersionId());
    JobPosting job =
        jobPostingRepository
            .findById(request.jobPostingId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Job posting not found: " + request.jobPostingId()));

    KeywordExtractor.ExtractedKeywords keywords =
        keywordExtractor.extract(user.getId(), job.getFullText());

    List<String> resumePieces = resumeTextPieces(resume);
    CoverageScorer.CoverageResult coverage =
        coverageScorer.score(
            keywords.required(), keywords.preferred(), coverageScorer.buildTermIndex(resumePieces));

    List<SemanticAugmenter.NearMiss> nearMisses =
        semanticAugmenter.findNearMisses(user.getId(), coverage.missingRequired(), resumePieces);
    List<FixGenerator.Fix> fixes =
        fixGenerator.build(coverage.missingRequired(), coverage.missingPreferred(), nearMisses);

    AtsGapReport.Breakdown breakdown =
        new AtsGapReport.Breakdown(
            coverage.requiredCoverage(), coverage.preferredCoverage(), coverage.keywordDensity());

    ScoringResult saved =
        scoringResultRepository.save(
            new ScoringResult(
                user.getId(),
                resume.getId(),
                job.getId(),
                BigDecimal.valueOf(coverage.score()).setScale(2, RoundingMode.HALF_UP),
                writeJson(breakdown),
                writeJson(coverage.missingRequired()),
                writeJson(coverage.missingPreferred()),
                writeJson(nearMisses),
                writeJson(fixes)));

    return new AtsGapReport(
        saved.getId(),
        resume.getId(),
        job.getId(),
        coverage.score(),
        breakdown,
        coverage.missingRequired(),
        coverage.missingPreferred(),
        nearMisses.stream()
            .map(n -> new AtsGapReport.NearMiss(n.skill(), n.similarity(), n.suggestedPhrase()))
            .toList(),
        fixes.stream()
            .map(f -> new AtsGapReport.Fix(f.skill(), f.suggestedBullet(), f.priority()))
            .toList(),
        saved.getScoredAt());
  }

  public AtsGapReport latest(String email, Long resumeVersionId) {
    User user = resolveUser(email);
    ownedResume(user, resumeVersionId);
    ScoringResult result =
        scoringResultRepository
            .findFirstByResumeVersionIdAndUserIdOrderByScoredAtDescIdDesc(
                resumeVersionId, user.getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No score yet for resume version: " + resumeVersionId));
    return new AtsGapReport(
        result.getId(),
        result.getResumeVersionId(),
        result.getJobPostingId(),
        result.getScore().doubleValue(),
        readJson(result.getBreakdown(), new TypeReference<AtsGapReport.Breakdown>() {}),
        readJson(result.getMissingRequired(), new TypeReference<List<String>>() {}),
        readJson(result.getMissingPreferred(), new TypeReference<List<String>>() {}),
        readJson(result.getNearMisses(), new TypeReference<List<AtsGapReport.NearMiss>>() {}),
        readJson(result.getFixes(), new TypeReference<List<AtsGapReport.Fix>>() {}),
        result.getScoredAt());
  }

  /** Flattens the resume JSON payload into scoreable text pieces: skills, titles, bullets, summary. */
  List<String> resumeTextPieces(ResumeVersion resume) {
    JsonNode payload;
    try {
      payload = objectMapper.readTree(resume.getJsonPayload());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Stored resume payload is corrupt for id " + resume.getId(), e);
    }
    List<String> pieces = new ArrayList<>();
    if (payload.hasNonNull("summary")) {
      pieces.add(payload.get("summary").asText());
    }
    if (payload.has("skills")) {
      payload.get("skills").forEach(skill -> pieces.add(skill.asText()));
    }
    if (payload.has("experience")) {
      for (JsonNode entry : payload.get("experience")) {
        if (entry.hasNonNull("title")) {
          pieces.add(entry.get("title").asText());
        }
        if (entry.has("bullets")) {
          entry.get("bullets").forEach(bullet -> pieces.add(bullet.asText()));
        }
      }
    }
    return pieces;
  }

  private ResumeVersion ownedResume(User user, Long resumeVersionId) {
    ResumeVersion version =
        resumeVersionRepository
            .findById(resumeVersionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Resume version not found: " + resumeVersionId));
    if (!version.getUserId().equals(user.getId())) {
      throw new ForbiddenException("Resume version belongs to another user");
    }
    return version;
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize scoring snapshot field", e);
    }
  }

  private <T> T readJson(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored scoring snapshot is corrupt", e);
    }
  }
}
