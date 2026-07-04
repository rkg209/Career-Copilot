package com.careercopilot.tracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "applications")
public class Application {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "job_posting_id")
  private Long jobPostingId;

  @Column(name = "job_title_snapshot", nullable = false)
  private String jobTitleSnapshot;

  @Column(name = "company_snapshot", nullable = false)
  private String companySnapshot;

  @Column(name = "resume_version_id")
  private Long resumeVersionId;

  @Column(name = "cover_letter_id")
  private Long coverLetterId;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "application_status")
  private ApplicationStatus status = ApplicationStatus.SAVED;

  @Column(name = "applied_at")
  private Instant appliedAt;

  private String notes;

  @Column(name = "source_url")
  private String sourceUrl;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Application() {}

  public Application(
      UUID userId,
      Long jobPostingId,
      String jobTitleSnapshot,
      String companySnapshot,
      Long resumeVersionId,
      Long coverLetterId,
      ApplicationStatus status,
      Instant appliedAt,
      String notes,
      String sourceUrl) {
    this.userId = userId;
    this.jobPostingId = jobPostingId;
    this.jobTitleSnapshot = jobTitleSnapshot;
    this.companySnapshot = companySnapshot;
    this.resumeVersionId = resumeVersionId;
    this.coverLetterId = coverLetterId;
    this.status = status;
    this.appliedAt = appliedAt;
    this.notes = notes;
    this.sourceUrl = sourceUrl;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getJobPostingId() {
    return jobPostingId;
  }

  public String getJobTitleSnapshot() {
    return jobTitleSnapshot;
  }

  public String getCompanySnapshot() {
    return companySnapshot;
  }

  public Long getResumeVersionId() {
    return resumeVersionId;
  }

  public void setResumeVersionId(Long resumeVersionId) {
    this.resumeVersionId = resumeVersionId;
  }

  public Long getCoverLetterId() {
    return coverLetterId;
  }

  public void setCoverLetterId(Long coverLetterId) {
    this.coverLetterId = coverLetterId;
  }

  public ApplicationStatus getStatus() {
    return status;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public void setAppliedAt(Instant appliedAt) {
    this.appliedAt = appliedAt;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
