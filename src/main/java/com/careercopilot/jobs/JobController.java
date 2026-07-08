package com.careercopilot.jobs;

import com.careercopilot.jobs.dto.IngestionStatusResponse;
import com.careercopilot.jobs.dto.JobPasteRequest;
import com.careercopilot.jobs.dto.JobPostingResponse;
import com.careercopilot.jobs.dto.JobSearchCriteria;
import com.careercopilot.jobs.dto.RolesResponse;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.PageResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

  private static final Set<String> REMOTE_TYPES = Set.of("remote", "hybrid", "onsite");
  private static final Set<String> EMPLOYMENT_TYPES = Set.of("full_time", "contract", "part_time");

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @GetMapping
  public PageResponse<JobPostingResponse> list(
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "postedAt,desc") String sort) {
    return jobService.list(page, size, sort, role);
  }

  @GetMapping("/roles")
  public RolesResponse roles() {
    return jobService.roles();
  }

  @GetMapping("/ingestion-status")
  public IngestionStatusResponse ingestionStatus() {
    return jobService.ingestionStatus();
  }

  @GetMapping("/search")
  public PageResponse<JobPostingResponse> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String remoteType,
      @RequestParam(required = false) String employmentType,
      @RequestParam(required = false) BigDecimal salaryMin,
      @RequestParam(required = false) String postedAfter,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "postedAt,desc") String sort) {
    JobSearchCriteria criteria =
        new JobSearchCriteria(
            q,
            location,
            toRemoteFilter(remoteType),
            validateEmploymentType(employmentType),
            salaryMin,
            toInstant(postedAfter),
            role);
    return jobService.search(criteria, page, size, sort);
  }

  @GetMapping("/{id}")
  public JobPostingResponse get(@PathVariable Long id) {
    return jobService.get(id);
  }

  @PostMapping("/paste")
  public ResponseEntity<JobPostingResponse> paste(
      @Valid @RequestBody JobPasteRequest request, Principal principal) {
    JobPostingResponse created = jobService.createManual(request, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  private static Boolean toRemoteFilter(String remoteType) {
    if (remoteType == null || remoteType.isBlank()) {
      return null;
    }
    if (!REMOTE_TYPES.contains(remoteType)) {
      throw new BadRequestException("remoteType must be one of " + REMOTE_TYPES);
    }
    return switch (remoteType) {
      case "remote" -> Boolean.TRUE;
      case "onsite" -> Boolean.FALSE;
      default -> null; // hybrid: no remote filter
    };
  }

  private static String validateEmploymentType(String employmentType) {
    if (employmentType == null || employmentType.isBlank()) {
      return null;
    }
    if (!EMPLOYMENT_TYPES.contains(employmentType)) {
      throw new BadRequestException("employmentType must be one of " + EMPLOYMENT_TYPES);
    }
    return employmentType;
  }

  private static Instant toInstant(String postedAfter) {
    if (postedAfter == null || postedAfter.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(postedAfter).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException e) {
      throw new BadRequestException("postedAfter must be an ISO date (YYYY-MM-DD)");
    }
  }
}
