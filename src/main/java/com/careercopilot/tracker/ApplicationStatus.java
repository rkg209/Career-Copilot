package com.careercopilot.tracker;

/** Maps to the Postgres {@code application_status} enum type. */
public enum ApplicationStatus {
  SAVED,
  APPLIED,
  PHONE_SCREEN,
  TECHNICAL_INTERVIEW,
  FINAL_ROUND,
  OFFER,
  REJECTED,
  WITHDRAWN
}
