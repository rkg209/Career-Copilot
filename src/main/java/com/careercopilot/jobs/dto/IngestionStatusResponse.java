package com.careercopilot.jobs.dto;

import java.time.Instant;

/**
 * Scraping-cadence status for the public jobs feed. {@code lastScrapedAt} is the most recent {@code
 * ingested_at} among scraped (non-manual-paste) postings — null if scraping has never completed
 * yet. {@code nextScrapedAt} is the next scheduled ingestion run computed from {@code
 * cronExpression}, regardless of whether the last run has completed.
 */
public record IngestionStatusResponse(
    Instant lastScrapedAt, Instant nextScrapedAt, String cronExpression) {}
