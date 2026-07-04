package com.careercopilot.tracker;

import com.careercopilot.intel.ContributionService;
import com.careercopilot.intel.dto.ContributeResponse;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.tracker.dto.ApplicationRequest;
import com.careercopilot.tracker.dto.ApplicationResponse;
import com.careercopilot.tracker.dto.InterviewLogRequest;
import com.careercopilot.tracker.dto.InterviewLogResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracker")
public class TrackerController {

  private final ApplicationService applicationService;
  private final InterviewLogService interviewLogService;
  private final ContributionService contributionService;

  public TrackerController(
      ApplicationService applicationService,
      InterviewLogService interviewLogService,
      ContributionService contributionService) {
    this.applicationService = applicationService;
    this.interviewLogService = interviewLogService;
    this.contributionService = contributionService;
  }

  @GetMapping("/applications")
  public PageResponse<ApplicationResponse> listApplications(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      Principal principal) {
    return applicationService.list(principal.getName(), page, size, sort);
  }

  @PostMapping("/applications")
  public ResponseEntity<ApplicationResponse> createApplication(
      @Valid @RequestBody ApplicationRequest request, Principal principal) {
    ApplicationResponse created = applicationService.create(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/applications/{id}")
  public ApplicationResponse updateApplication(
      @PathVariable Long id, @Valid @RequestBody ApplicationRequest request, Principal principal) {
    return applicationService.update(principal.getName(), id, request);
  }

  @GetMapping("/applications/{id}/interview-logs")
  public List<InterviewLogResponse> listInterviewLogs(@PathVariable Long id, Principal principal) {
    return interviewLogService.list(principal.getName(), id);
  }

  @PostMapping("/applications/{id}/interview-logs")
  public ResponseEntity<InterviewLogResponse> addInterviewLog(
      @PathVariable Long id, @Valid @RequestBody InterviewLogRequest request, Principal principal) {
    InterviewLogResponse created = interviewLogService.add(principal.getName(), id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PostMapping("/applications/{id}/interview-logs/{logId}/contribute")
  public ResponseEntity<ContributeResponse> contribute(
      @PathVariable Long id, @PathVariable Long logId, Principal principal) {
    ContributeResponse response = contributionService.contribute(principal.getName(), id, logId);
    return ResponseEntity.ok(response);
  }
}
