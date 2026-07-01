package com.careercopilot.jobs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API representation of a job posting (used for both list and detail views). */
public record JobPostingResponse(
    Long id,
    String source,
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
    Instant postedAt) {}
