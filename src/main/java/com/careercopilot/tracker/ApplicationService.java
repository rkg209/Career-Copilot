package com.careercopilot.tracker;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tailoring.CoverLetterRepository;
import com.careercopilot.tailoring.ResumeVersionRepository;
import com.careercopilot.tracker.dto.ApplicationRequest;
import com.careercopilot.tracker.dto.ApplicationResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD over {@code applications} plus status-machine-validated transitions. */
@Service
@Transactional
public class ApplicationService {

  private static final int MAX_PAGE_SIZE = 100;

  private final ApplicationRepository applicationRepository;
  private final UserRepository userRepository;
  private final JobPostingRepository jobPostingRepository;
  private final ResumeVersionRepository resumeVersionRepository;
  private final CoverLetterRepository coverLetterRepository;

  public ApplicationService(
      ApplicationRepository applicationRepository,
      UserRepository userRepository,
      JobPostingRepository jobPostingRepository,
      ResumeVersionRepository resumeVersionRepository,
      CoverLetterRepository coverLetterRepository) {
    this.applicationRepository = applicationRepository;
    this.userRepository = userRepository;
    this.jobPostingRepository = jobPostingRepository;
    this.resumeVersionRepository = resumeVersionRepository;
    this.coverLetterRepository = coverLetterRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<ApplicationResponse> list(String email, int page, int size, String sort) {
    User user = resolveUser(email);
    Pageable pageable =
        PageRequest.of(page, validatedSize(size), ApplicationSortMapper.listSort(sort));
    Page<Application> result = applicationRepository.findByUserId(user.getId(), pageable);
    List<ApplicationResponse> content = result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(result, content);
  }

  public ApplicationResponse create(String email, ApplicationRequest request) {
    User user = resolveUser(email);

    JobPosting job = null;
    if (request.jobPostingId() != null) {
      job =
          jobPostingRepository
              .findById(request.jobPostingId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Job posting not found: " + request.jobPostingId()));
    }

    String jobTitleSnapshot = request.jobTitleSnapshot();
    String companySnapshot = request.companySnapshot();
    if (job != null) {
      jobTitleSnapshot = jobTitleSnapshot != null ? jobTitleSnapshot : job.getTitle();
      companySnapshot = companySnapshot != null ? companySnapshot : job.getCompany();
    }
    if (jobTitleSnapshot == null || jobTitleSnapshot.isBlank()) {
      throw new BadRequestException("jobTitleSnapshot is required when jobPostingId is omitted");
    }
    if (companySnapshot == null || companySnapshot.isBlank()) {
      throw new BadRequestException("companySnapshot is required when jobPostingId is omitted");
    }

    if (request.resumeVersionId() != null) {
      assertOwnedResumeVersion(user.getId(), request.resumeVersionId());
    }
    if (request.coverLetterId() != null) {
      assertOwnedCoverLetter(user.getId(), request.coverLetterId());
    }

    ApplicationStatus status =
        request.status() != null ? request.status() : ApplicationStatus.SAVED;
    ApplicationStatusMachine.assertAllowed(ApplicationStatus.SAVED, status);
    Instant appliedAt = resolveAppliedAt(status, request.appliedAt());

    Application saved =
        applicationRepository.save(
            new Application(
                user.getId(),
                request.jobPostingId(),
                jobTitleSnapshot,
                companySnapshot,
                request.resumeVersionId(),
                request.coverLetterId(),
                status,
                appliedAt,
                request.notes(),
                request.sourceUrl()));
    return toResponse(saved);
  }

  public ApplicationResponse update(String email, Long id, ApplicationRequest request) {
    User user = resolveUser(email);
    Application application = ownedApplication(user, id);

    if (request.status() != null) {
      ApplicationStatusMachine.assertAllowed(application.getStatus(), request.status());
      Instant appliedAt = resolveAppliedAt(request.status(), request.appliedAt());
      application.setStatus(request.status());
      application.setAppliedAt(appliedAt != null ? appliedAt : application.getAppliedAt());
    } else if (request.appliedAt() != null) {
      application.setAppliedAt(request.appliedAt());
    }

    if (request.resumeVersionId() != null) {
      assertOwnedResumeVersion(user.getId(), request.resumeVersionId());
      application.setResumeVersionId(request.resumeVersionId());
    }
    if (request.coverLetterId() != null) {
      assertOwnedCoverLetter(user.getId(), request.coverLetterId());
      application.setCoverLetterId(request.coverLetterId());
    }
    if (request.notes() != null) {
      application.setNotes(request.notes());
    }
    if (request.sourceUrl() != null) {
      application.setSourceUrl(request.sourceUrl());
    }

    return toResponse(application);
  }

  public Application ownedApplication(User user, Long id) {
    Application application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    if (!application.getUserId().equals(user.getId())) {
      throw new ForbiddenException("Application belongs to another user");
    }
    return application;
  }

  public User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }

  private Instant resolveAppliedAt(ApplicationStatus status, Instant requestedAppliedAt) {
    if (requestedAppliedAt != null) {
      return requestedAppliedAt;
    }
    return status == ApplicationStatus.APPLIED ? Instant.now() : null;
  }

  private void assertOwnedResumeVersion(UUID userId, Long resumeVersionId) {
    var resume =
        resumeVersionRepository
            .findById(resumeVersionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Resume version not found: " + resumeVersionId));
    if (!resume.getUserId().equals(userId)) {
      throw new ForbiddenException("Resume version belongs to another user");
    }
  }

  private void assertOwnedCoverLetter(UUID userId, Long coverLetterId) {
    var coverLetter =
        coverLetterRepository
            .findById(coverLetterId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Cover letter not found: " + coverLetterId));
    if (!coverLetter.getUserId().equals(userId)) {
      throw new ForbiddenException("Cover letter belongs to another user");
    }
  }

  private static int validatedSize(int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return size;
  }

  private ApplicationResponse toResponse(Application a) {
    return new ApplicationResponse(
        a.getId(),
        a.getJobPostingId(),
        a.getJobTitleSnapshot(),
        a.getCompanySnapshot(),
        a.getResumeVersionId(),
        a.getCoverLetterId(),
        a.getStatus(),
        a.getAppliedAt(),
        a.getNotes(),
        a.getSourceUrl(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }
}
