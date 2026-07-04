package com.careercopilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.tailoring.CoverLetterRepository;
import com.careercopilot.tailoring.ResumeVersionRepository;
import com.careercopilot.tracker.dto.InterviewLogRequest;
import com.careercopilot.tracker.dto.InterviewLogResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewLogServiceTest {

  @Mock private InterviewLogRepository interviewLogRepository;
  @Mock private ApplicationRepository applicationRepository;
  @Mock private UserRepository userRepository;
  @Mock private JobPostingRepository jobPostingRepository;
  @Mock private ResumeVersionRepository resumeVersionRepository;
  @Mock private CoverLetterRepository coverLetterRepository;

  private InterviewLogService service() {
    ApplicationService applicationService =
        new ApplicationService(
            applicationRepository,
            userRepository,
            jobPostingRepository,
            resumeVersionRepository,
            coverLetterRepository);
    return new InterviewLogService(interviewLogRepository, applicationService);
  }

  private User userWithId(UUID id) {
    User user = org.mockito.Mockito.mock(User.class);
    org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
    return user;
  }

  private Application ownedApplication(UUID userId) {
    return new Application(
        userId, null, "Engineer", "Acme", null, null, ApplicationStatus.SAVED, null, null, null);
  }

  @Test
  void add_ownedApplication_savesLog() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    when(applicationRepository.findById(1L)).thenReturn(Optional.of(ownedApplication(userId)));
    when(interviewLogRepository.save(any(InterviewLog.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    InterviewLogRequest request =
        new InterviewLogRequest(
            InterviewRoundType.TECHNICAL,
            2,
            null,
            60,
            "Senior Engineer",
            List.of("Reverse a linked list"),
            null,
            null,
            "PASSED",
            4);

    InterviewLogResponse response = service().add("me@example.com", 1L, request);

    assertThat(response.roundType()).isEqualTo(InterviewRoundType.TECHNICAL);
    assertThat(response.roundNumber()).isEqualTo(2);
    assertThat(response.difficulty()).isEqualTo(4);
  }

  @Test
  void add_otherUsersApplication_throwsForbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID intruderId = UUID.randomUUID();
    User intruder = userWithId(intruderId);
    when(userRepository.findByEmail("intruder@example.com")).thenReturn(Optional.of(intruder));
    when(applicationRepository.findById(1L)).thenReturn(Optional.of(ownedApplication(ownerId)));

    InterviewLogRequest request =
        new InterviewLogRequest(
            InterviewRoundType.TECHNICAL, 1, null, null, null, List.of(), null, null, null, null);

    assertThatThrownBy(() -> service().add("intruder@example.com", 1L, request))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void add_defaultsRoundNumberToOne() {
    UUID userId = UUID.randomUUID();
    User user = userWithId(userId);
    when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
    when(applicationRepository.findById(1L)).thenReturn(Optional.of(ownedApplication(userId)));
    when(interviewLogRepository.save(any(InterviewLog.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    InterviewLogRequest request =
        new InterviewLogRequest(
            InterviewRoundType.PHONE_SCREEN,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null);

    InterviewLogResponse response = service().add("me@example.com", 1L, request);

    assertThat(response.roundNumber()).isEqualTo(1);
  }
}
