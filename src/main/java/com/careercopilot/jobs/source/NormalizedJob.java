package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal, source-agnostic representation of a job posting produced by a {@link JobSourceAdapter}
 * before it is persisted. Every adapter maps its provider payload into this shape.
 */
public record NormalizedJob(
    JobSource source,
    String externalId,
    String title,
    String company,
    String location,
    boolean remote,
    String employmentType,
    BigDecimal salaryMin,
    BigDecimal salaryMax,
    String salaryCurrency,
    List<String> skills,
    List<String> responsibilities,
    String fullText,
    String url,
    Instant postedAt,
    Instant expiresAt,
    UUID pastedByUserId) {}
