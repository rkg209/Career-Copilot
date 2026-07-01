package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.careercopilot.jobs.dto.JobPasteRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns a user's pasted JD into a {@link NormalizedJob}. Not a scheduled {@link JobSourceAdapter}:
 * it is driven synchronously by {@code JobService.createManual}. External id is null (manual rows
 * are not deduplicated); the raw text becomes the full text.
 */
@Component
public class ManualPasteJobSource {

  public NormalizedJob normalize(JobPasteRequest req, UUID userId) {
    return new NormalizedJob(
        JobSource.MANUAL_PASTE,
        null,
        req.title(),
        req.company(),
        req.location(),
        false,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        req.rawText(),
        req.url(),
        null,
        null,
        userId);
  }
}
