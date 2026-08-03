package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.careercopilot.intel.dto.CompanyIntelDetail;
import com.careercopilot.intel.dto.CompanyIntelSummary;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tracker.InterviewRoundType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class IntelServiceTest {

  @Mock private CompanyIntelRepository companyIntelRepository;

  private IntelService service() {
    return new IntelService(companyIntelRepository);
  }

  private CompanyIntelEntry publishedEntry(
      String company, InterviewRoundType roundType, String question, Integer difficulty) {
    CompanyIntelEntry entry =
        new CompanyIntelEntry(
            company,
            "Software Engineer",
            roundType,
            List.of(question),
            "Two rounds",
            difficulty,
            UUID.randomUUID());
    entry.publish(java.time.Instant.now());
    return entry;
  }

  @Test
  void browseCompanies_aggregatesOnlyPublishedRows() {
    when(companyIntelRepository.findDistinctPublishedCompanies(PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of("Acme Corp"), PageRequest.of(0, 20), 1));
    when(companyIntelRepository.findByCompanyInAndPublishedTrue(List.of("Acme Corp")))
        .thenReturn(
            List.of(
                publishedEntry(
                    "Acme Corp", InterviewRoundType.PHONE_SCREEN, "Tell me about yourself", 2),
                publishedEntry(
                    "Acme Corp", InterviewRoundType.TECHNICAL, "Reverse a linked list", 4)));

    PageResponse<CompanyIntelSummary> result = service().browseCompanies(PageRequest.of(0, 20));

    assertThat(result.content()).hasSize(1);
    CompanyIntelSummary summary = result.content().get(0);
    assertThat(summary.company()).isEqualTo("Acme Corp");
    assertThat(summary.entryCount()).isEqualTo(2);
    assertThat(summary.roundTypes())
        .containsExactlyInAnyOrder(InterviewRoundType.PHONE_SCREEN, InterviewRoundType.TECHNICAL);
    assertThat(summary.avgDifficulty()).isEqualTo(3.0);
  }

  @Test
  void byCompany_groupsByRoundType() {
    when(companyIntelRepository.findByCompanyAndPublishedTrue("Acme Corp"))
        .thenReturn(
            List.of(
                publishedEntry(
                    "Acme Corp", InterviewRoundType.TECHNICAL, "Reverse a linked list", 4),
                publishedEntry(
                    "Acme Corp", InterviewRoundType.TECHNICAL, "Design a rate limiter", 5)));

    CompanyIntelDetail detail = service().byCompany("Acme Corp");

    assertThat(detail.company()).isEqualTo("Acme Corp");
    assertThat(detail.rounds()).hasSize(1);
    CompanyIntelDetail.RoundSummary round = detail.rounds().get(0);
    assertThat(round.roundType()).isEqualTo(InterviewRoundType.TECHNICAL);
    assertThat(round.entryCount()).isEqualTo(2);
    assertThat(round.questions())
        .containsExactlyInAnyOrder("Reverse a linked list", "Design a rate limiter");
  }

  @Test
  void byCompany_noPublishedRows_throws404() {
    when(companyIntelRepository.findByCompanyAndPublishedTrue("Unknown Co")).thenReturn(List.of());

    assertThatThrownBy(() -> service().byCompany("Unknown Co"))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
