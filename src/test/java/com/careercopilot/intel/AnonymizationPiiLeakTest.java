package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.careercopilot.shared.PiiRedactionService;
import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.ApplicationStatus;
import com.careercopilot.tracker.InterviewLog;
import com.careercopilot.tracker.InterviewRoundType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Guardrail test: a log crafted with every known PII pattern must produce an intel row containing
 * none of it, regardless of which free-text field the PII appears in.
 */
@ExtendWith(MockitoExtension.class)
class AnonymizationPiiLeakTest {

  @Mock private CompanyIntelRepository companyIntelRepository;

  private final PiiScrubber piiScrubber = new PiiScrubber(new PiiRedactionService());

  @Test
  void anonymizeAndStage_craftedLogWithEmbeddedPii_leaksNothing() {
    when(companyIntelRepository.findByCompanyAndPublishedFalse("Acme Corp")).thenReturn(List.of());
    when(companyIntelRepository.save(any(CompanyIntelEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    String pii =
        "Contact Jane Doe at jane.doe@acme.com or 415-555-0134, see https://linkedin.com/in/janedoe";
    InterviewLog log =
        new InterviewLog(
            1L,
            UUID.randomUUID(),
            InterviewRoundType.TECHNICAL,
            1,
            null,
            60,
            "Senior Engineer",
            List.of("Question referencing " + pii),
            pii,
            pii,
            "PASSED",
            4);
    Application application =
        new Application(
            UUID.randomUUID(),
            null,
            "Software Engineer",
            "Acme Corp",
            null,
            null,
            ApplicationStatus.SAVED,
            null,
            null,
            null);

    CompanyIntelEntry entry =
        new AnonymizationService(piiScrubber, companyIntelRepository)
            .anonymizeAndStage(log, application);

    assertThat(entry.getQuestions()).noneMatch(q -> q.contains("jane.doe@acme.com"));
    assertThat(entry.getQuestions()).noneMatch(q -> q.contains("415-555-0134"));
    assertThat(entry.getQuestions()).noneMatch(q -> q.contains("Jane Doe"));
    assertThat(entry.getQuestions()).noneMatch(q -> q.contains("linkedin.com"));
    assertThat(entry.getProcessNotes())
        .doesNotContain("jane.doe@acme.com")
        .doesNotContain("415-555-0134")
        .doesNotContain("Jane Doe")
        .doesNotContain("linkedin.com");
  }
}
