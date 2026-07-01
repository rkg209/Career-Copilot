package com.careercopilot.jobs;

import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls each enabled {@link JobSourceAdapter} and upserts the results. Every adapter runs in its
 * own try/catch so one failing source never aborts the others. Deduplication is by (source,
 * external_id): an existing row is updated in place, otherwise a new row is inserted.
 */
@Service
public class JobIngestionService {

  private static final Logger log = LoggerFactory.getLogger(JobIngestionService.class);

  private final List<JobSourceAdapter> adapters;
  private final JobPostingRepository repository;
  private final Set<JobSource> enabled;

  public JobIngestionService(
      List<JobSourceAdapter> adapters,
      JobPostingRepository repository,
      @Value("${jobs.adapters.enabled:REMOTEOK}") Set<JobSource> enabled) {
    this.adapters = adapters;
    this.repository = repository;
    this.enabled = enabled;
  }

  @Scheduled(cron = "${jobs.ingestion.cron:0 0 */6 * * *}")
  public void ingestAll() {
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

  @Transactional
  public IngestionResult ingestFrom(JobSourceAdapter adapter) {
    List<NormalizedJob> fetched = adapter.fetch();
    int inserted = 0;
    int updated = 0;
    for (NormalizedJob n : fetched) {
      var existing =
          n.externalId() == null
              ? java.util.Optional.<JobPosting>empty()
              : repository.findBySourceAndExternalId(n.source(), n.externalId());
      if (existing.isPresent()) {
        JobPostingMapper.merge(existing.get(), n);
        repository.save(existing.get());
        updated++;
      } else {
        repository.save(JobPostingMapper.toEntity(n));
        inserted++;
      }
    }
    IngestionResult result = new IngestionResult(adapter.id(), fetched.size(), inserted, updated);
    log.info(
        "Ingested source={} fetched={} inserted={} updated={}",
        result.source(),
        result.fetched(),
        result.inserted(),
        result.updated());
    return result;
  }

  /** Per-source ingestion summary (also used by tests). */
  public record IngestionResult(JobSource source, int fetched, int inserted, int updated) {}
}
