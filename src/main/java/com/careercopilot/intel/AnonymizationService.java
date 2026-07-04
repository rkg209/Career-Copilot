package com.careercopilot.intel;

import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.InterviewLog;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sole writer of {@code company_interview_intel}. Converts a private {@link InterviewLog} (plus its
 * owning {@link Application}, for company/title) into an anonymized {@link CompanyIntelEntry} — no
 * user id, no application id, free text scrubbed.
 */
@Service
public class AnonymizationService {

  private static final Map<String, String> ROLE_KEYWORDS =
      Map.of(
          "engineer", "Software Engineer",
          "developer", "Software Engineer",
          "product manager", "Product Manager",
          "data scientist", "Data Scientist",
          "designer", "Designer",
          "analyst", "Analyst");

  private final PiiScrubber piiScrubber;
  private final CompanyIntelRepository companyIntelRepository;

  public AnonymizationService(
      PiiScrubber piiScrubber, CompanyIntelRepository companyIntelRepository) {
    this.piiScrubber = piiScrubber;
    this.companyIntelRepository = companyIntelRepository;
  }

  /** Anonymizes {@code log}, saves it staged (unpublished), and returns the persisted row. */
  public CompanyIntelEntry anonymizeAndStage(InterviewLog log, Application application) {
    String company = application.getCompanySnapshot().trim();
    String roleCategory = normalizeRoleCategory(application.getJobTitleSnapshot());
    List<String> scrubbedQuestions =
        log.getQuestionsAsked().stream().map(piiScrubber::scrub).toList();
    String processNotes =
        piiScrubber.scrub(combineNotes(log.getNotes(), log.getFeedbackReceived()));

    var stagingBatchId =
        companyIntelRepository.findByCompanyAndPublishedFalse(company).stream()
            .findFirst()
            .map(CompanyIntelEntry::getStagingBatchId)
            .orElseGet(UUID::randomUUID);

    CompanyIntelEntry entry =
        new CompanyIntelEntry(
            company,
            roleCategory,
            log.getRoundType(),
            scrubbedQuestions,
            processNotes,
            log.getDifficulty(),
            stagingBatchId);
    return companyIntelRepository.save(entry);
  }

  private String combineNotes(String notes, String feedbackReceived) {
    if (notes == null || notes.isBlank()) {
      return feedbackReceived;
    }
    if (feedbackReceived == null || feedbackReceived.isBlank()) {
      return notes;
    }
    return notes + "\n" + feedbackReceived;
  }

  private String normalizeRoleCategory(String jobTitleSnapshot) {
    if (jobTitleSnapshot == null || jobTitleSnapshot.isBlank()) {
      return null;
    }
    String lower = jobTitleSnapshot.toLowerCase(Locale.ROOT);
    for (var entry : ROLE_KEYWORDS.entrySet()) {
      if (lower.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return jobTitleSnapshot.trim();
  }
}
