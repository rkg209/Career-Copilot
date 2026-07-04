package com.careercopilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tailoring.CoverLetterRepository;
import com.careercopilot.tailoring.ResumeVersionRepository;
import com.careercopilot.tracker.dto.ApplicationRequest;
import com.careercopilot.tracker.dto.ApplicationResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  @Mock private ApplicationRepository applicationRepository;
  @Mock private UserRepository userRepository;
  @Mock private JobPostingRepository jobPostingRepository;
  @Mock private ResumeVersionRepository resumeVersionRepository;
  @Mock private CoverLetterRepository coverLetterRepository;

  private ApplicationService service() {
    return new ApplicationService(
        applicationRepository,
        userRepository,
        jobPostingRepository,
        resumeVersionRepository,
        coverLetterRepository);
  }

  private User userWithId(UUID id) {
    User user = org.mockito.Mockito.mock(User.class);
    org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
    return user;
  }

  @Test
  void create_defaultsSnapshotFromLinkedJobPosting() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    JobPosting job = org.mockito.Mockito.mock(JobPosting.class);
    when(job.getTitle()).thenReturn("Backend Engineer");
    when(job.getCompany()).thenReturn("Acme");
    when(jobPostingRepository.findById(42L)).thenReturn(Optional.of(job));
    when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

    ApplicationRequest request =
        new ApplicationRequest(42L, null, null, null, null, null, null, null, null);
    ApplicationResponse response = service().create("me@example.com", request);

    assertThat(response.jobTitleSnapshot()).isEqualTo("Backend Engineer");
    assertThat(response.companySnapshot()).isEqualTo("Acme");
    assertThat(response.status()).isEqualTo(ApplicationStatus.SAVED);
  }

  @Test
  void create_withoutJobPostingOrSnapshot_throwsBadRequest() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));

    ApplicationRequest request =
        new ApplicationRequest(null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service().create("me@example.com", request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void create_directToApplied_succeedsAndSetsAppliedAt() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

    ApplicationRequest request =
        new ApplicationRequest(
            null, "Engineer", "Acme", null, null, ApplicationStatus.APPLIED, null, null, null);
    ApplicationResponse response = service().create("me@example.com", request);

    assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
    assertThat(response.appliedAt()).isNotNull();
  }

  @Test
  void create_illegalInitialStatus_throwsBadRequest() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));

    ApplicationRequest request =
        new ApplicationRequest(
            null,
            "Engineer",
            "Acme",
            null,
            null,
            ApplicationStatus.TECHNICAL_INTERVIEW,
            null,
            null,
            null);

    assertThatThrownBy(() -> service().create("me@example.com", request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void update_illegalTransition_throwsBadRequest() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    Application application =
        new Application(
            userId,
            null,
            "Engineer",
            "Acme",
            null,
            null,
            ApplicationStatus.SAVED,
            null,
            null,
            null);
    when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

    ApplicationRequest request =
        new ApplicationRequest(
            null, null, null, null, null, ApplicationStatus.OFFER, null, null, null);

    assertThatThrownBy(() -> service().update("me@example.com", 1L, request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void update_otherUsersApplication_throwsForbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID intruderId = UUID.randomUUID();
    User intruder = userWithId(intruderId);
    when(userRepository.findByEmail("intruder@example.com")).thenReturn(Optional.of(intruder));
    Application application =
        new Application(
            ownerId,
            null,
            "Engineer",
            "Acme",
            null,
            null,
            ApplicationStatus.SAVED,
            null,
            null,
            null);
    when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

    ApplicationRequest request =
        new ApplicationRequest(null, null, null, null, null, null, null, "note", null);

    assertThatThrownBy(() -> service().update("intruder@example.com", 1L, request))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void update_unknownApplication_throwsNotFound() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

    ApplicationRequest request =
        new ApplicationRequest(null, null, null, null, null, null, null, "note", null);

    assertThatThrownBy(() -> service().update("me@example.com", 999L, request))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
