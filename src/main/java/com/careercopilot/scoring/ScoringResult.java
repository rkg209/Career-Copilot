package com.careercopilot.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One persisted ATS gap-report snapshot per score run (V12). */
@Entity
@Table(name = "scoring_results")
public class ScoringResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "resume_version_id", nullable = false)
  private Long resumeVersionId;

  @Column(name = "job_posting_id", nullable = false)
  private Long jobPostingId;

  @Column(name = "score", nullable = false)
  private BigDecimal score;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "breakdown", nullable = false, columnDefinition = "jsonb")
  private String breakdown;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "missing_required", nullable = false, columnDefinition = "jsonb")
  private String missingRequired;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "missing_preferred", nullable = false, columnDefinition = "jsonb")
  private String missingPreferred;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "near_misses", nullable = false, columnDefinition = "jsonb")
  private String nearMisses;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fixes", nullable = false, columnDefinition = "jsonb")
  private String fixes;

  @CreationTimestamp
  @Column(name = "scored_at", nullable = false, updatable = false)
  private Instant scoredAt;

  protected ScoringResult() {}

  public ScoringResult(
      UUID userId,
      Long resumeVersionId,
      Long jobPostingId,
      BigDecimal score,
      String breakdown,
      String missingRequired,
      String missingPreferred,
      String nearMisses,
      String fixes) {
    this.userId = userId;
    this.resumeVersionId = resumeVersionId;
    this.jobPostingId = jobPostingId;
    this.score = score;
    this.breakdown = breakdown;
    this.missingRequired = missingRequired;
    this.missingPreferred = missingPreferred;
    this.nearMisses = nearMisses;
    this.fixes = fixes;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getResumeVersionId() {
    return resumeVersionId;
  }

  public Long getJobPostingId() {
    return jobPostingId;
  }

  public BigDecimal getScore() {
    return score;
  }

  public String getBreakdown() {
    return breakdown;
  }

  public String getMissingRequired() {
    return missingRequired;
  }

  public String getMissingPreferred() {
    return missingPreferred;
  }

  public String getNearMisses() {
    return nearMisses;
  }

  public String getFixes() {
    return fixes;
  }

  public Instant getScoredAt() {
    return scoredAt;
  }
}
