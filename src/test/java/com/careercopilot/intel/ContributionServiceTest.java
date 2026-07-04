package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.intel.dto.ContributeResponse;
import com.careercopilot.tracker.AlreadyContributedException;
import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.ApplicationService;
import com.careercopilot.tracker.ApplicationStatus;
import com.careercopilot.tracker.ContributionStatus;
import com.careercopilot.tracker.InterviewLog;
import com.careercopilot.tracker.InterviewLogRepository;
import com.careercopilot.tracker.InterviewRoundType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

  @Mock private ApplicationService applicationService;
  @Mock private ApplicationRepository applicationRepository;
  @Mock private InterviewLogRepository interviewLogRepository;
  @Mock private AnonymizationService anonymizationService;
  @Mock private CompanyIntelRepository companyIntelRepository;

  private ContributionService service(int threshold) {
    return new ContributionService(
        applicationService,
        applicationRepository,
        interviewLogRepository,
        anonymizationService,
        companyIntelRepository,
        threshold);
  }

  private User userWithId(UUID id) {
    User user = org.mockito.Mockito.mock(User.class);
    org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
    return user;
  }

  private Application application(UUID userId) {
    return new Application(
        userId,
        null,
        "Software Engineer",
        "Acme Corp",
        null,
        null,
        ApplicationStatus.SAVED,
        null,
        null,
        null);
  }

  private InterviewLog log(UUID userId) {
    return new InterviewLog(
        1L,
        userId,
        InterviewRoundType.TECHNICAL,
        1,
        null,
        60,
        null,
        List.of(),
        null,
        null,
        null,
        null);
  }

  @Test
  void contribute_belowThreshold_returnsStaged() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    Application application = application(userId);
    InterviewLog log = log(userId);
    when(applicationService.resolveUser("me@example.com")).thenReturn(user);
    when(applicationService.ownedApplication(user, 1L)).thenReturn(application);
    when(interviewLogRepository.findByIdAndApplicationIdAndUserId(10L, 1L, userId))
        .thenReturn(Optional.of(log));
    when(companyIntelRepository.countByCompany("Acme Corp")).thenReturn(1L);

    ContributeResponse response = service(3).contribute("me@example.com", 1L, 10L);

    assertThat(response.status()).isEqualTo("STAGED");
    assertThat(response.company()).isEqualTo("Acme Corp");
    assertThat(response.threshold()).isEqualTo(3);
    assertThat(log.getContributionStatus()).isEqualTo(ContributionStatus.STAGED);
    assertThat(log.getContributedAt()).isNotNull();
  }

  @Test
  void contribute_meetsThreshold_publishesStagedBatchAndReturnsPublished() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    Application application = application(userId);
    InterviewLog log = log(userId);
    when(applicationService.resolveUser("me@example.com")).thenReturn(user);
    when(applicationService.ownedApplication(user, 1L)).thenReturn(application);
    when(interviewLogRepository.findByIdAndApplicationIdAndUserId(10L, 1L, userId))
        .thenReturn(Optional.of(log));
    when(companyIntelRepository.countByCompany("Acme Corp")).thenReturn(3L);

    CompanyIntelEntry staged =
        new CompanyIntelEntry(
            "Acme Corp",
            "Software Engineer",
            InterviewRoundType.TECHNICAL,
            List.of(),
            null,
            null,
            UUID.randomUUID());
    when(companyIntelRepository.findByCompanyAndPublishedFalse("Acme Corp"))
        .thenReturn(List.of(staged));
    when(applicationRepository.findIdsByCompanySnapshot("Acme Corp")).thenReturn(List.of(1L));
    when(interviewLogRepository.findByApplicationIdInAndContributionStatus(
            List.of(1L), ContributionStatus.STAGED))
        .thenReturn(List.of(log));

    ContributeResponse response = service(3).contribute("me@example.com", 1L, 10L);

    assertThat(response.status()).isEqualTo("PUBLISHED");
    assertThat(staged.isPublished()).isTrue();
    assertThat(staged.getStagingBatchId()).isNull();
    assertThat(log.getContributionStatus()).isEqualTo(ContributionStatus.PUBLISHED);
  }

  @Test
  void contribute_alreadyContributed_throws409() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    Application application = application(userId);
    InterviewLog log = log(userId);
    log.setContributedAt(Instant.now());
    when(applicationService.resolveUser("me@example.com")).thenReturn(user);
    when(applicationService.ownedApplication(user, 1L)).thenReturn(application);
    when(interviewLogRepository.findByIdAndApplicationIdAndUserId(10L, 1L, userId))
        .thenReturn(Optional.of(log));

    assertThatThrownBy(() -> service(3).contribute("me@example.com", 1L, 10L))
        .isInstanceOf(AlreadyContributedException.class);
    verify(anonymizationService, org.mockito.Mockito.never()).anonymizeAndStage(any(), any());
  }
}
