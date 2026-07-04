package com.careercopilot.intel;

import com.careercopilot.auth.User;
import com.careercopilot.intel.dto.ContributeResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tracker.AlreadyContributedException;
import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.ApplicationService;
import com.careercopilot.tracker.ContributionStatus;
import com.careercopilot.tracker.InterviewLog;
import com.careercopilot.tracker.InterviewLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates {@code contribute}: ownership check → anonymize → stage → publish once the
 * per-company threshold is met. Replaces the Phase 5 stub in the tracker feature.
 */
@Service
@Transactional
public class ContributionService {

  private final ApplicationService applicationService;
  private final ApplicationRepository applicationRepository;
  private final InterviewLogRepository interviewLogRepository;
  private final AnonymizationService anonymizationService;
  private final CompanyIntelRepository companyIntelRepository;
  private final int minAggregationThreshold;

  public ContributionService(
      ApplicationService applicationService,
      ApplicationRepository applicationRepository,
      InterviewLogRepository interviewLogRepository,
      AnonymizationService anonymizationService,
      CompanyIntelRepository companyIntelRepository,
      @Value("${careercopilot.intel.min-aggregation-threshold:3}") int minAggregationThreshold) {
    this.applicationService = applicationService;
    this.applicationRepository = applicationRepository;
    this.interviewLogRepository = interviewLogRepository;
    this.anonymizationService = anonymizationService;
    this.companyIntelRepository = companyIntelRepository;
    this.minAggregationThreshold = minAggregationThreshold;
  }

  public ContributeResponse contribute(String email, Long applicationId, Long logId) {
    User user = applicationService.resolveUser(email);
    Application application = applicationService.ownedApplication(user, applicationId);
    InterviewLog log = ownedLog(applicationId, logId, user.getId());
    if (log.getContributedAt() != null) {
      throw new AlreadyContributedException("Interview log already contributed: " + logId);
    }

    anonymizationService.anonymizeAndStage(log, application);
    log.setContributedAt(Instant.now());
    log.setContributionStatus(ContributionStatus.STAGED);

    String company = application.getCompanySnapshot().trim();
    long count = companyIntelRepository.countByCompany(company);
    if (count >= minAggregationThreshold) {
      publishCompany(company);
      return new ContributeResponse("PUBLISHED", company, (int) count, minAggregationThreshold);
    }
    return new ContributeResponse("STAGED", company, (int) count, minAggregationThreshold);
  }

  private void publishCompany(String company) {
    Instant publishedAt = Instant.now();
    companyIntelRepository
        .findByCompanyAndPublishedFalse(company)
        .forEach(e -> e.publish(publishedAt));

    List<Long> applicationIds = applicationRepository.findIdsByCompanySnapshot(company);
    interviewLogRepository
        .findByApplicationIdInAndContributionStatus(applicationIds, ContributionStatus.STAGED)
        .forEach(log -> log.setContributionStatus(ContributionStatus.PUBLISHED));
  }

  private InterviewLog ownedLog(Long applicationId, Long logId, UUID userId) {
    return interviewLogRepository
        .findByIdAndApplicationIdAndUserId(logId, applicationId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Interview log not found: " + logId));
  }
}
