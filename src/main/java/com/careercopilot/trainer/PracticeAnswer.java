package com.careercopilot.trainer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "practice_answers")
public class PracticeAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "mock_question_id", nullable = false)
  private Long mockQuestionId;

  @Column(name = "answer_text", nullable = false)
  private String answerText;

  @Column(name = "quality_grade")
  private Integer qualityGrade;

  private String feedback;

  @Column(name = "llm_model")
  private String llmModel;

  @Column(name = "token_count")
  private Integer tokenCount;

  @CreationTimestamp
  @Column(name = "answered_at", nullable = false, updatable = false)
  private Instant answeredAt;

  protected PracticeAnswer() {}

  public PracticeAnswer(
      UUID userId,
      Long mockQuestionId,
      String answerText,
      Integer qualityGrade,
      String feedback,
      String llmModel,
      Integer tokenCount) {
    this.userId = userId;
    this.mockQuestionId = mockQuestionId;
    this.answerText = answerText;
    this.qualityGrade = qualityGrade;
    this.feedback = feedback;
    this.llmModel = llmModel;
    this.tokenCount = tokenCount;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getMockQuestionId() {
    return mockQuestionId;
  }

  public String getAnswerText() {
    return answerText;
  }

  public Integer getQualityGrade() {
    return qualityGrade;
  }

  public String getFeedback() {
    return feedback;
  }

  public String getLlmModel() {
    return llmModel;
  }

  public Integer getTokenCount() {
    return tokenCount;
  }

  public Instant getAnsweredAt() {
    return answeredAt;
  }
}
