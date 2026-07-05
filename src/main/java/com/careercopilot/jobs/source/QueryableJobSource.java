package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import java.util.List;

/**
 * A search-based job source: given a {@link RoleQuery} (role keywords + location) it returns
 * matching postings. Distinct from {@link JobSourceAdapter}, which fetches a whole board with no
 * query. Aggregators (Adzuna, Jooble) implement this; ingestion drives them once per role×location.
 * A source with missing credentials should return an empty list (logged), never throw on the
 * scheduled path.
 */
public interface QueryableJobSource {

  /** The provenance this source produces. */
  JobSource id();

  /** Whether this source is usable (e.g. credentials present). Unusable sources are skipped. */
  boolean isEnabled();

  /** Search and normalize the current matches for one role/location query. */
  List<NormalizedJob> fetch(RoleQuery query);
}
