package com.careercopilot.profile;

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
@Table(name = "education_entries")
public class EducationEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String degree;

  @Column(nullable = false)
  private String institution;

  private String university;

  private String specialization;

  @Column(name = "start_year")
  private Integer startYear;

  @Column(name = "end_year")
  private Integer endYear;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "score_type", columnDefinition = "education_score_type")
  private EducationScoreType scoreType;

  @Column(name = "score_value")
  private String scoreValue;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected EducationEntry() {}

  public EducationEntry(UUID userId) {
    this.userId = userId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDegree() {
    return degree;
  }

  public void setDegree(String degree) {
    this.degree = degree;
  }

  public String getInstitution() {
    return institution;
  }

  public void setInstitution(String institution) {
    this.institution = institution;
  }

  public String getUniversity() {
    return university;
  }

  public void setUniversity(String university) {
    this.university = university;
  }

  public String getSpecialization() {
    return specialization;
  }

  public void setSpecialization(String specialization) {
    this.specialization = specialization;
  }

  public Integer getStartYear() {
    return startYear;
  }

  public void setStartYear(Integer startYear) {
    this.startYear = startYear;
  }

  public Integer getEndYear() {
    return endYear;
  }

  public void setEndYear(Integer endYear) {
    this.endYear = endYear;
  }

  public EducationScoreType getScoreType() {
    return scoreType;
  }

  public void setScoreType(EducationScoreType scoreType) {
    this.scoreType = scoreType;
  }

  public String getScoreValue() {
    return scoreValue;
  }

  public void setScoreValue(String scoreValue) {
    this.scoreValue = scoreValue;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
