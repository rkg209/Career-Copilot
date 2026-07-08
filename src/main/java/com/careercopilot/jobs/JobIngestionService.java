package com.careercopilot.jobs;

import static java.util.stream.Collectors.toSet;

import com.careercopilot.jobs.source.JobRelevanceFilter;
import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.jobs.source.QueryableJobSource;
import com.careercopilot.jobs.source.RoleQuery;
import com.careercopilot.jobs.source.RoleTagger;
import com.careercopilot.jobs.source.SkillExtractor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs two ingestion passes and upserts the results. (A) A role-driven pass queries each enabled
 * {@link QueryableJobSource} (aggregators) once per role×location, tagging results with the query
 * role. (B) A whole-board pass polls each enabled {@link JobSourceAdapter} (ATS/RemoteOK), applies
 * the relevance filter, and tags results with every role whose keywords match. Every source runs in
 * its own try/catch so one failure never aborts the others. Deduplication is by (source,
 * external_id); on re-ingest the row is updated in place and its {@code matchedRoles} are unioned.
 */
@Service
public class JobIngestionService {

  private static final Logger log = LoggerFactory.getLogger(JobIngestionService.class);

  private final List<JobSourceAdapter> adapters;
  private final List<QueryableJobSource> queryableSources;
  private final JobPostingRepository repository;
  private final JobRelevanceFilter relevanceFilter;
  private final SkillExtractor skillExtractor;
  private final RoleTagger roleTagger;
  private final RoleProperties roleProperties;
  private final Set<JobSource> enabled;
  private final int freshnessMaxAgeDays;

  public JobIngestionService(
      List<JobSourceAdapter> adapters,
      List<QueryableJobSource> queryableSources,
      JobPostingRepository repository,
      JobRelevanceFilter relevanceFilter,
      SkillExtractor skillExtractor,
      RoleTagger roleTagger,
      RoleProperties roleProperties,
      @Value("${jobs.adapters.enabled:REMOTEOK}") Set<JobSource> enabled,
      @Value("${jobs.freshness.max-age-days:45}") int freshnessMaxAgeDays) {
    this.adapters = adapters;
    this.queryableSources = queryableSources;
    this.repository = repository;
    this.relevanceFilter = relevanceFilter;
    this.skillExtractor = skillExtractor;
    this.roleTagger = roleTagger;
    this.roleProperties = roleProperties;
    this.enabled = enabled;
    this.freshnessMaxAgeDays = freshnessMaxAgeDays;
  }

  @Scheduled(cron = "${jobs.ingestion.cron:0 0 */12 * * *}")
  public void ingestAll() {
    ingestFromQueryableSources();
    for (JobSourceAdapter adapter : adapters) {
      if (!enabled.contains(adapter.id())) {
        continue;
      }
      try {
        ingestFrom(adapter);
      } catch (Exception e) {
        log.error("Ingestion failed for source {}: {}", adapter.id(), e.getMessage(), e);
      }
    }
  }

  /**
   * Role-driven pass: for each role×location, query every enabled aggregator and upsert the results
   * tagged with the query role. Each (source, role, location) unit runs in its own try/catch.
   */
  @Transactional
  public void ingestFromQueryableSources() {
    roleProperties
        .getRoles()
        .forEach(
            (roleKey, role) -> {
              for (String location : roleProperties.getSearchLocations()) {
                RoleQuery query = new RoleQuery(roleKey, role.getKeywords(), location);
                for (QueryableJobSource source : queryableSources) {
                  if (!enabled.contains(source.id()) || !source.isEnabled()) {
                    continue;
                  }
                  try {
                    ingestQuery(source, query);
                  } catch (Exception e) {
                    log.error(
                        "Aggregator {} failed for role={} location={}: {}",
                        source.id(),
                        roleKey,
                        location,
                        e.getMessage(),
                        e);
                  }
                }
              }
            });
  }

  private void ingestQuery(QueryableJobSource source, RoleQuery query) {
    List<NormalizedJob> results = source.fetch(query);
    Set<String> roles = Set.of(query.roleKey());
    int inserted = 0;
    int updated = 0;
    for (NormalizedJob raw : results) {
      if (upsert(enrichSkills(raw), roles) == UpsertOutcome.INSERTED) {
        inserted++;
      } else {
        updated++;
      }
    }
    log.info(
        "Queried source={} role={} location={} fetched={} inserted={} updated={}",
        source.id(),
        query.roleKey(),
        query.location(),
        results.size(),
        inserted,
        updated);
  }

  @Transactional
  public IngestionResult ingestFrom(JobSourceAdapter adapter) {
    List<NormalizedJob> fetched = adapter.fetch();
    int inserted = 0;
    int updated = 0;
    int filteredOut = 0;
    for (NormalizedJob raw : fetched) {
      if (!relevanceFilter.isRelevant(raw)) {
        filteredOut++;
        continue;
      }
      NormalizedJob n = enrichSkills(raw);
      if (upsert(n, roleTagger.tag(n)) == UpsertOutcome.INSERTED) {
        inserted++;
      } else {
        updated++;
      }
    }
    int deactivated = deactivateMissingFrom(adapter.id(), fetched);
    IngestionResult result =
        new IngestionResult(adapter.id(), fetched.size(), inserted, updated, filteredOut);
    log.info(
        "Ingested source={} fetched={} kept={} filteredOut={} inserted={} updated={} deactivated={}",
        result.source(),
        result.fetched(),
        result.kept(),
        result.filteredOut(),
        result.inserted(),
        result.updated(),
        deactivated);
    return result;
  }

  /**
   * A whole-board fetch is exhaustive, so any active posting from this source not present in the
   * latest fetch has disappeared upstream (closed/filled/removed) and is deactivated. Skipped
   * entirely when the fetch returned nothing, since an empty list is ambiguous with a
   * failed/short-circuited call and would otherwise wrongly deactivate the whole source.
   */
  private int deactivateMissingFrom(JobSource source, List<NormalizedJob> fetched) {
    if (fetched.isEmpty()) {
      return 0;
    }
    Set<String> seenExternalIds =
        fetched.stream().map(NormalizedJob::externalId).filter(Objects::nonNull).collect(toSet());
    if (seenExternalIds.isEmpty()) {
      return 0;
    }
    return repository.deactivateMissingExternalIds(source, seenExternalIds);
  }

  /**
   * Upserts a normalized posting, unioning the given roles into {@code matchedRoles}. Source fields
   * are overwritten on update; roles accumulate across sources and runs.
   */
  private UpsertOutcome upsert(NormalizedJob n, Set<String> roles) {
    Optional<JobPosting> existing =
        n.externalId() == null
            ? Optional.empty()
            : repository.findBySourceAndExternalId(n.source(), n.externalId());
    if (existing.isPresent()) {
      JobPosting entity = existing.get();
      JobPostingMapper.merge(entity, n);
      entity.setMatchedRoles(union(entity.getMatchedRoles(), roles));
      repository.save(entity);
      return UpsertOutcome.UPDATED;
    }
    JobPosting entity = JobPostingMapper.toEntity(n);
    entity.setMatchedRoles(new ArrayList<>(roles));
    repository.save(entity);
    return UpsertOutcome.INSERTED;
  }

  private static List<String> union(List<String> existing, Set<String> add) {
    Set<String> merged = new LinkedHashSet<>(existing == null ? List.of() : existing);
    merged.addAll(add);
    return new ArrayList<>(merged);
  }

  /** Populates skills from the description when the source provided none; otherwise unchanged. */
  private NormalizedJob enrichSkills(NormalizedJob n) {
    if (n.skills() != null && !n.skills().isEmpty()) {
      return n;
    }
    return n.withSkills(skillExtractor.extract(n.title(), n.fullText()));
  }

  /** Freshness sweep: deactivates postings older than the configured cutoff. */
  @Scheduled(cron = "${jobs.freshness.sweep-cron:0 30 * * * *}")
  @Transactional
  public int deactivateStale() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(freshnessMaxAgeDays));
    int deactivated = repository.deactivateOlderThan(cutoff);
    log.info("Freshness sweep deactivated={} cutoff={}", deactivated, cutoff);
    return deactivated;
  }

  private enum UpsertOutcome {
    INSERTED,
    UPDATED
  }

  /**
   * Per-source ingestion summary (also used by tests). {@code kept == inserted + updated} and
   * {@code fetched == kept + filteredOut}.
   */
  public record IngestionResult(
      JobSource source, int fetched, int inserted, int updated, int filteredOut) {

    public int kept() {
      return inserted + updated;
    }
  }
}
