package com.careercopilot.jobs.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized search filters. {@code remote} is derived from the API's {@code remoteType}: {@code
 * remote} → true, {@code onsite} → false, {@code hybrid} → null (no filter).
 */
public record JobSearchCriteria(
    String q,
    String location,
    Boolean remote,
    String employmentType,
    BigDecimal salaryMin,
    Instant postedAfter) {}
