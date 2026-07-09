package com.careercopilot.profile;

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
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "experience_entries")
public class ExperienceEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  private String company;

  private String location;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  private String description;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "entry_type", nullable = false, columnDefinition = "corpus_entry_type")
  private CorpusEntryType entryType = CorpusEntryType.WORK;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "project_type", columnDefinition = "project_type")
  private ProjectType projectType;

  private String guide;

  private String context;

  private String url;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private List<String> achievements = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "skills_used", nullable = false, columnDefinition = "text[]")
  private List<String> skillsUsed = new ArrayList<>();

  @Array(length = 768)
  @JdbcTypeCode(SqlTypes.VECTOR)
  @Column(columnDefinition = "vector(768)")
  private float[] embedding;

  @Column(name = "text_hash", length = 64)
  private String textHash;

  @Column(name = "embedded_at")
  private Instant embeddedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ExperienceEntry() {}

  public ExperienceEntry(UUID userId) {
    this.userId = userId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public boolean isCurrent() {
    return current;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public CorpusEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(CorpusEntryType entryType) {
    this.entryType = entryType;
  }

  public ProjectType getProjectType() {
    return projectType;
  }

  public void setProjectType(ProjectType projectType) {
    this.projectType = projectType;
  }

  public String getGuide() {
    return guide;
  }

  public void setGuide(String guide) {
    this.guide = guide;
  }

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public List<String> getAchievements() {
    return achievements;
  }

  public void setAchievements(List<String> achievements) {
    this.achievements = achievements;
  }

  public List<String> getSkillsUsed() {
    return skillsUsed;
  }

  public void setSkillsUsed(List<String> skillsUsed) {
    this.skillsUsed = skillsUsed;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }

  public String getTextHash() {
    return textHash;
  }

  public void setTextHash(String textHash) {
    this.textHash = textHash;
  }

  public Instant getEmbeddedAt() {
    return embeddedAt;
  }

  public void setEmbeddedAt(Instant embeddedAt) {
    this.embeddedAt = embeddedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
