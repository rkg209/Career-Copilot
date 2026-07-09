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
@Table(name = "profile_highlights")
public class ProfileHighlight {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "highlight_type")
  private HighlightType type;

  @Column(nullable = false)
  private String text;

  @Column(name = "year_label")
  private String yearLabel;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProfileHighlight() {}

  public ProfileHighlight(UUID userId) {
    this.userId = userId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public HighlightType getType() {
    return type;
  }

  public void setType(HighlightType type) {
    this.type = type;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getYearLabel() {
    return yearLabel;
  }

  public void setYearLabel(String yearLabel) {
    this.yearLabel = yearLabel;
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
