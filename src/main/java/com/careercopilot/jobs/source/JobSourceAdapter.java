package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import java.util.List;

/**
 * Strategy interface for an external job feed. Adding a new source = implementing this interface as
 * a Spring bean; ingestion and the controller never change. Named {@code ...Adapter} to avoid
 * clashing with the {@link JobSource} enum.
 */
public interface JobSourceAdapter {

  /** The provenance this adapter produces. */
  JobSource id();

  /** Fetch and normalize the current batch of postings from the external source. */
  List<NormalizedJob> fetch();
}
