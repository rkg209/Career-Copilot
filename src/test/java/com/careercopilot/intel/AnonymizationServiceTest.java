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

@ExtendWith(MockitoExtension.class)
class AnonymizationServiceTest {

  @Mock private CompanyIntelRepository companyIntelRepository;

  private final PiiScrubber piiScrubber = new PiiScrubber(new PiiRedactionService());

  private AnonymizationService service() {
    return new AnonymizationService(piiScrubber, companyIntelRepository);
  }

  private Application application(String company, String title) {
    return new Application(
        UUID.randomUUID(),
        null,
        title,
        company,
        null,
        null,
        ApplicationStatus.SAVED,
        null,
        null,
        null);
  }

  private InterviewLog log(List<String> questions, String notes, String feedback) {
    return new InterviewLog(
        1L,
        UUID.randomUUID(),
        InterviewRoundType.TECHNICAL,
        1,
        null,
        60,
        "Senior Engineer",
        questions,
        notes,
        feedback,
        "PASSED",
        4);
  }

  @Test
  void anonymizeAndStage_hasNoUserOrApplicationFields() {
    when(companyIntelRepository.findByCompanyAndPublishedFalse("Acme Corp")).thenReturn(List.of());
    when(companyIntelRepository.save(any(CompanyIntelEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CompanyIntelEntry entry =
        service()
            .anonymizeAndStage(
                log(List.of("Reverse a linked list"), "Went well", "Strong hire"),
                application("Acme Corp", "Software Engineer"));

    assertThat(entry.getCompany()).isEqualTo("Acme Corp");
    assertThat(entry.getRoleCategory()).isEqualTo("Software Engineer");
    assertThat(entry.isPublished()).isFalse();
    assertThat(entry.getStagingBatchId()).isNotNull();
    // Compile-time guarantee: CompanyIntelEntry has no user_id/application_id fields at all.
  }

  @Test
  void anonymizeAndStage_scrubsPiiFromQuestionsAndNotes() {
    when(companyIntelRepository.findByCompanyAndPublishedFalse("Acme Corp")).thenReturn(List.of());
    when(companyIntelRepository.save(any(CompanyIntelEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CompanyIntelEntry entry =
        service()
            .anonymizeAndStage(
                log(
                    List.of("Email John Smith at john.smith@acme.com to confirm"),
                    "Interviewer contacted me at 415-555-0134",
                    null),
                application("Acme Corp", "Software Engineer"));

    assertThat(entry.getQuestions().get(0))
        .doesNotContain("john.smith@acme.com")
        .doesNotContain("John Smith");
    assertThat(entry.getProcessNotes()).doesNotContain("415-555-0134");
  }

  @Test
  void anonymizeAndStage_reusesExistingStagingBatchForCompany() {
    UUID existingBatch = UUID.randomUUID();
    CompanyIntelEntry existing =
        new CompanyIntelEntry(
            "Acme Corp",
            "Software Engineer",
            InterviewRoundType.PHONE_SCREEN,
            List.of(),
            null,
            null,
            existingBatch);
    when(companyIntelRepository.findByCompanyAndPublishedFalse("Acme Corp"))
        .thenReturn(List.of(existing));
    when(companyIntelRepository.save(any(CompanyIntelEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CompanyIntelEntry entry =
        service()
            .anonymizeAndStage(
                log(List.of(), "notes", null), application("Acme Corp", "Software Engineer"));

    assertThat(entry.getStagingBatchId()).isEqualTo(existingBatch);
  }
}
