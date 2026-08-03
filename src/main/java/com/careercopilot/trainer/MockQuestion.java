package com.careercopilot.trainer;

import com.careercopilot.tracker.InterviewRoundType;
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
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mock_questions")
public class MockQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  private String company;

  @Column(name = "role_category")
  private String roleCategory;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "round_type", columnDefinition = "interview_round_type")
  private InterviewRoundType roundType;

  @Column(name = "question_text", nullable = false)
  private String questionText;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "mock_question_source")
  private MockQuestionSource source;

  @Column(name = "source_ref_id")
  private Long sourceRefId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MockQuestion() {}

  public MockQuestion(
      UUID userId,
      String company,
      String roleCategory,
      InterviewRoundType roundType,
      String questionText,
      MockQuestionSource source,
      Long sourceRefId) {
    this.userId = userId;
    this.company = company;
    this.roleCategory = roleCategory;
    this.roundType = roundType;
    this.questionText = questionText;
    this.source = source;
    this.sourceRefId = sourceRefId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
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

  public String getQuestionText() {
    return questionText;
  }

  public MockQuestionSource getSource() {
    return source;
  }

  public Long getSourceRefId() {
    return sourceRefId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
