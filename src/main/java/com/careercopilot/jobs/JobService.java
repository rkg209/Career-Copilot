package com.careercopilot.jobs;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.dto.JobPasteRequest;
import com.careercopilot.jobs.dto.JobPostingResponse;
import com.careercopilot.jobs.dto.JobSearchCriteria;
import com.careercopilot.jobs.source.ManualPasteJobSource;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobService {

  private static final int MAX_PAGE_SIZE = 100;

  private final JobPostingRepository repository;
  private final ManualPasteJobSource manualPasteJobSource;
  private final UserRepository userRepository;

  public JobService(
      JobPostingRepository repository,
      ManualPasteJobSource manualPasteJobSource,
      UserRepository userRepository) {
    this.repository = repository;
    this.manualPasteJobSource = manualPasteJobSource;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<JobPostingResponse> list(int page, int size, String sort) {
    Pageable pageable = PageRequest.of(page, validatedSize(size), JobSortMapper.listSort(sort));
    Page<JobPosting> result = repository.findByActiveTrue(pageable);
    return toPageResponse(result);
  }

  @Transactional(readOnly = true)
  public PageResponse<JobPostingResponse> search(
      JobSearchCriteria criteria, int page, int size, String sort) {
    Pageable pageable = PageRequest.of(page, validatedSize(size), JobSortMapper.searchSort(sort));
    Page<JobPosting> result =
        repository.search(
            blankToNull(criteria.q()),
            blankToNull(criteria.location()),
            criteria.remote(),
            blankToNull(criteria.employmentType()),
            criteria.salaryMin(),
            criteria.postedAfter(),
            pageable);
    return toPageResponse(result);
  }

  @Transactional(readOnly = true)
  public JobPostingResponse get(Long id) {
    JobPosting posting =
        repository
            .findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));
    return JobPostingMapper.toResponse(posting);
  }

  public JobPostingResponse createManual(JobPasteRequest req, String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    NormalizedJob normalized = manualPasteJobSource.normalize(req, user.getId());
    JobPosting saved = repository.save(JobPostingMapper.toEntity(normalized));
    return JobPostingMapper.toResponse(saved);
  }

  private static int validatedSize(int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return size;
  }

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  private static PageResponse<JobPostingResponse> toPageResponse(Page<JobPosting> page) {
    List<JobPostingResponse> content =
        page.getContent().stream().map(JobPostingMapper::toResponse).toList();
    return PageResponse.of(page, content);
  }
}
