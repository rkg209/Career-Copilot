package com.careercopilot.intel;

import com.careercopilot.tracker.InterviewRoundType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SHARED zone: no user_id, no application_id, by design. Rows are written only by {@link
 * AnonymizationService} and read (for the public browse endpoints) only where {@code isPublished}
 * is true.
 */
@Entity
@Table(name = "company_interview_intel")
public class CompanyIntelEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String company;

  @Column(name = "role_category")
  private String roleCategory;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "round_type", nullable = false, columnDefinition = "interview_round_type")
  private InterviewRoundType roundType;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private List<String> questions = new ArrayList<>();

  @Column(name = "process_notes")
  private String processNotes;

  private Integer difficulty;

  @Column(name = "is_published", nullable = false)
  private boolean published;

  @Column(name = "staging_batch_id")
  private UUID stagingBatchId;

  @Column(name = "published_at")
  private Instant publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CompanyIntelEntry() {}

  public CompanyIntelEntry(
      String company,
      String roleCategory,
      InterviewRoundType roundType,
      List<String> questions,
      String processNotes,
      Integer difficulty,
      UUID stagingBatchId) {
    this.company = company;
    this.roleCategory = roleCategory;
    this.roundType = roundType;
    this.questions = questions == null ? new ArrayList<>() : questions;
    this.processNotes = processNotes;
    this.difficulty = difficulty;
    this.published = false;
    this.stagingBatchId = stagingBatchId;
  }

  public Long getId() {
    return id;
  }

  public String getCompany() {
    return company;
  }

  public String getRoleCategory() {
    return roleCategory;
  }

  public InterviewRoundType getRoundType() {
    return roundType;
  }

  public List<String> getQuestions() {
    return questions;
  }

  public String getProcessNotes() {
    return processNotes;
  }

  public Integer getDifficulty() {
    return difficulty;
  }

  public boolean isPublished() {
    return published;
  }

  public UUID getStagingBatchId() {
    return stagingBatchId;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void publish(Instant publishedAt) {
    this.published = true;
    this.publishedAt = publishedAt;
    this.stagingBatchId = null;
  }
}
