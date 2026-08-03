package com.careercopilot.trainer;

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
import org.hibernate.annotations.UpdateTimestamp;

/** One SM-2 spaced-repetition card per user+question; state applied by {@link Sm2Scheduler}. */
@Entity
@Table(name = "review_cards")
public class ReviewCard {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "mock_question_id", nullable = false)
  private Long mockQuestionId;

  @Column(name = "ease_factor", nullable = false)
  private BigDecimal easeFactor;

  @Column(name = "interval_days", nullable = false)
  private int intervalDays;

  @Column(nullable = false)
  private int repetitions;

  @Column(name = "last_grade")
  private Integer lastGrade;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReviewCard() {}

  public ReviewCard(UUID userId, Long mockQuestionId) {
    this.userId = userId;
    this.mockQuestionId = mockQuestionId;
    this.easeFactor = Sm2Scheduler.INITIAL_EF;
    this.intervalDays = 0;
    this.repetitions = 0;
    this.dueAt = Instant.now();
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

  public BigDecimal getEaseFactor() {
    return easeFactor;
  }

  public int getIntervalDays() {
    return intervalDays;
  }

  public int getRepetitions() {
    return repetitions;
  }

  public Integer getLastGrade() {
    return lastGrade;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void apply(Sm2Scheduler.Sm2State state, int grade) {
    this.easeFactor = state.easeFactor();
    this.intervalDays = state.intervalDays();
    this.repetitions = state.repetitions();
    this.dueAt = state.dueAt();
    this.lastGrade = grade;
  }
}
