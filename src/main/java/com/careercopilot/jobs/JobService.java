package com.careercopilot.jobs;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.dto.IngestionStatusResponse;
import com.careercopilot.jobs.dto.JobPasteRequest;
import com.careercopilot.jobs.dto.JobPostingResponse;
import com.careercopilot.jobs.dto.JobSearchCriteria;
import com.careercopilot.jobs.dto.RolePresetResponse;
import com.careercopilot.jobs.dto.RolesResponse;
import com.careercopilot.jobs.source.ManualPasteJobSource;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.CandidateProfileRepository;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobService {

  private static final int MAX_PAGE_SIZE = 100;

  private final JobPostingRepository repository;
  private final ManualPasteJobSource manualPasteJobSource;
  private final UserRepository userRepository;
  private final CandidateProfileRepository profileRepository;
  private final RoleProperties roleProperties;
  private final String ingestionCron;

  public JobService(
      JobPostingRepository repository,
      ManualPasteJobSource manualPasteJobSource,
      UserRepository userRepository,
      CandidateProfileRepository profileRepository,
      RoleProperties roleProperties,
      @Value("${jobs.ingestion.cron:0 0 */12 * * *}") String ingestionCron) {
    this.repository = repository;
    this.manualPasteJobSource = manualPasteJobSource;
    this.userRepository = userRepository;
    this.profileRepository = profileRepository;
    this.roleProperties = roleProperties;
    this.ingestionCron = ingestionCron;
  }

  @Transactional(readOnly = true)
  public PageResponse<JobPostingResponse> list(int page, int size, String sort, String role) {
    String validRole = validatedRole(role);
    if (validRole == null) {
      Pageable pageable = PageRequest.of(page, validatedSize(size), JobSortMapper.listSort(sort));
      return toPageResponse(repository.findByActiveTrue(pageable));
    }
    // Role filter uses the native containment query, so sort maps to physical column names.
    Pageable pageable = PageRequest.of(page, validatedSize(size), JobSortMapper.searchSort(sort));
    return toPageResponse(repository.findByRole(validRole, pageable));
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
            validatedRole(criteria.role()),
            pageable);
    return toPageResponse(result);
  }

  /**
   * "My Preference" feed: active postings matching the caller's profile target roles / areas of
   * interest. Returns an empty page (rather than "match everything") when the profile has no
   * preferences set.
   */
  @Transactional(readOnly = true)
  public PageResponse<JobPostingResponse> forMe(String email, int page, int size, String sort) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    List<String> terms =
        profileRepository
            .findByUserId(user.getId())
            .map(JobService::preferenceTerms)
            .orElse(List.of());
    Pageable pageable = PageRequest.of(page, validatedSize(size), JobSortMapper.searchSort(sort));
    String tsQuery = toTsQuery(terms);
    if (tsQuery.isEmpty()) {
      return toPageResponse(Page.empty(pageable));
    }
    return toPageResponse(repository.findByPreferenceTerms(tsQuery, pageable));
  }

  /**
   * Builds a {@code to_tsquery}-ready expression that ORs each free-text term (its words ANDed
   * together) — e.g. {@code ["Backend Engineer", "Machine Learning"]} becomes {@code "(backend &
   * engineer) | (machine & learning)"}. Non-word characters are stripped rather than escaped, since
   * {@code to_tsquery} (unlike {@code plainto_tsquery}/{@code websearch_to_query}, neither of which
   * this Postgres image provides) has no plain-text mode and would otherwise throw on stray
   * punctuation.
   */
  private static String toTsQuery(List<String> terms) {
    return terms.stream()
        .map(JobService::tsQueryWordGroup)
        .filter(group -> !group.isEmpty())
        .map(group -> "(" + group + ")")
        .collect(Collectors.joining(" | "));
  }

  private static String tsQueryWordGroup(String term) {
    return Stream.of(term.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        .filter(word -> !word.isEmpty())
        .collect(Collectors.joining(" & "));
  }

  private static List<String> preferenceTerms(CandidateProfile profile) {
    return Stream.concat(profile.getTargetRoles().stream(), profile.getAreasOfInterest().stream())
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .toList();
  }

  /** The available role presets, in configured display order. */
  @Transactional(readOnly = true)
  public RolesResponse roles() {
    List<RolePresetResponse> presets =
        roleProperties.getRoles().entrySet().stream()
            .map(e -> new RolePresetResponse(e.getKey(), e.getValue().getLabel()))
            .toList();
    return new RolesResponse(presets);
  }

  /**
   * Scraping cadence status: when scraping last actually completed (excludes manual pastes) and
   * when the next scheduled run is due, computed live from the ingestion cron expression.
   */
  @Transactional(readOnly = true)
  public IngestionStatusResponse ingestionStatus() {
    Instant lastScrapedAt = repository.findLastScrapedAt(JobSource.MANUAL_PASTE).orElse(null);
    Instant nextScrapedAt =
        CronExpression.parse(ingestionCron).next(ZonedDateTime.now(ZoneOffset.UTC)).toInstant();
    return new IngestionStatusResponse(lastScrapedAt, nextScrapedAt, ingestionCron);
  }

  /** Null/blank → no filter; a non-preset key → 400. */
  private String validatedRole(String role) {
    String r = blankToNull(role);
    if (r != null && !roleProperties.getRoles().containsKey(r)) {
      throw new BadRequestException("Unknown role: " + role);
    }
    return r;
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
