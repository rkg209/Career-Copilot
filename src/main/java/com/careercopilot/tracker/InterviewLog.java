package com.careercopilot.tracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "interview_logs")
public class InterviewLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "application_id", nullable = false)
  private Long applicationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "round_type", nullable = false, columnDefinition = "interview_round_type")
  private InterviewRoundType roundType;

  @Column(name = "round_number", nullable = false)
  private int roundNumber = 1;

  @Column(name = "interview_date")
  private LocalDate interviewDate;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "interviewer_role")
  private String interviewerRole;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "questions_asked", nullable = false, columnDefinition = "text[]")
  private List<String> questionsAsked = new ArrayList<>();

  private String notes;

  @Column(name = "feedback_received")
  private String feedbackReceived;

  private String outcome;

  private Integer difficulty;

  @Column(name = "contributed_at")
  private Instant contributedAt;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "contribution_status", columnDefinition = "contribution_status")
  private ContributionStatus contributionStatus;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InterviewLog() {}

  public InterviewLog(
      Long applicationId,
      UUID userId,
      InterviewRoundType roundType,
      int roundNumber,
      LocalDate interviewDate,
      Integer durationMinutes,
      String interviewerRole,
      List<String> questionsAsked,
      String notes,
      String feedbackReceived,
      String outcome,
      Integer difficulty) {
    this.applicationId = applicationId;
    this.userId = userId;
    this.roundType = roundType;
    this.roundNumber = roundNumber;
    this.interviewDate = interviewDate;
    this.durationMinutes = durationMinutes;
    this.interviewerRole = interviewerRole;
    this.questionsAsked = questionsAsked == null ? new ArrayList<>() : questionsAsked;
    this.notes = notes;
    this.feedbackReceived = feedbackReceived;
    this.outcome = outcome;
    this.difficulty = difficulty;
  }

  public Long getId() {
    return id;
  }

  public Long getApplicationId() {
    return applicationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public InterviewRoundType getRoundType() {
    return roundType;
  }

  public int getRoundNumber() {
    return roundNumber;
  }

  public LocalDate getInterviewDate() {
    return interviewDate;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public String getInterviewerRole() {
    return interviewerRole;
  }

  public List<String> getQuestionsAsked() {
    return questionsAsked;
  }

  public String getNotes() {
    return notes;
  }

  public String getFeedbackReceived() {
    return feedbackReceived;
  }

  public String getOutcome() {
    return outcome;
  }

  public Integer getDifficulty() {
    return difficulty;
  }

  public Instant getContributedAt() {
    return contributedAt;
  }

  public void setContributedAt(Instant contributedAt) {
    this.contributedAt = contributedAt;
  }

  public ContributionStatus getContributionStatus() {
    return contributionStatus;
  }

  public void setContributionStatus(ContributionStatus contributionStatus) {
    this.contributionStatus = contributionStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
