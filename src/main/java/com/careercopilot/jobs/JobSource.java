package com.careercopilot.jobs;

/** Provenance of a job posting. Maps to the Postgres {@code job_source} enum type. */
public enum JobSource {
  ADZUNA,
  REMOTEOK,
  USAJOBS,
  MANUAL_PASTE,
  GREENHOUSE,
  LEVER
}
