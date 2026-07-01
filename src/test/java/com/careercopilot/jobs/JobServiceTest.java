package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.dto.JobPasteRequest;
import com.careercopilot.jobs.dto.JobPostingResponse;
import com.careercopilot.jobs.dto.JobSearchCriteria;
import com.careercopilot.jobs.dto.PageResponse;
import com.careercopilot.jobs.source.ManualPasteJobSource;
import com.careercopilot.shared.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock private JobPostingRepository repository;
  @Mock private UserRepository userRepository;

  private JobService service() {
    return new JobService(repository, new ManualPasteJobSource(), userRepository);
  }

  @Test
  void createManual_buildsManualPastePosting() {
    UUID userId = UUID.randomUUID();
    User user = org.mockito.Mockito.mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    when(repository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

    JobPasteRequest req =
        new JobPasteRequest("Backend Engineer", "Acme", "Remote", "https://x", "a".repeat(60));
    JobPostingResponse response = service().createManual(req, "me@example.com");

    assertThat(response.source()).isEqualTo(JobSource.MANUAL_PASTE.name());
    assertThat(response.title()).isEqualTo("Backend Engineer");
    assertThat(response.externalId()).isNull();
    assertThat(response.fullText()).hasSize(60);
  }

  @Test
  void list_mapsPageEnvelope() {
    JobPosting entity =
        JobPostingMapper.toEntity(
            new com.careercopilot.jobs.source.NormalizedJob(
                JobSource.REMOTEOK,
                "1",
                "Engineer",
                "Acme",
                "Remote",
                true,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "body",
                "https://x",
                null,
                null,
                null));
    Pageable pageable = PageRequest.of(0, 20);
    Page<JobPosting> page = new PageImpl<>(List.of(entity), pageable, 1);
    when(repository.findByActiveTrue(any(Pageable.class))).thenReturn(page);

    PageResponse<JobPostingResponse> result = service().list(0, 20, "postedAt,desc");

    assertThat(result.content()).hasSize(1);
    assertThat(result.page().totalElements()).isEqualTo(1);
    assertThat(result.page().size()).isEqualTo(20);
  }

  @Test
  void list_sizeAboveMax_throwsBadRequest() {
    assertThatThrownBy(() -> service().list(0, 101, "postedAt,desc"))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void search_passesMappedCriteriaToRepository() {
    Page<JobPosting> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    when(repository.search(
            eq("java"),
            eq("berlin"),
            eq(Boolean.TRUE),
            isNull(),
            any(BigDecimal.class),
            isNull(),
            any(Pageable.class)))
        .thenReturn(empty);

    JobSearchCriteria criteria =
        new JobSearchCriteria(
            "java", "berlin", Boolean.TRUE, null, BigDecimal.valueOf(50000), null);
    PageResponse<JobPostingResponse> result = service().search(criteria, 0, 20, "postedAt,desc");

    assertThat(result.content()).isEmpty();
    verify(repository)
        .search(
            eq("java"),
            eq("berlin"),
            eq(Boolean.TRUE),
            isNull(),
            any(BigDecimal.class),
            isNull(),
            any(Pageable.class));
  }
}
