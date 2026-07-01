package com.careercopilot.profile;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  private String headline;

  private String summary;

  private String location;

  @Column(name = "linkedin_url")
  private String linkedinUrl;

  @Column(name = "github_url")
  private String githubUrl;

  @Column(name = "portfolio_url")
  private String portfolioUrl;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "target_roles", nullable = false, columnDefinition = "text[]")
  private List<String> targetRoles = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "target_locations", nullable = false, columnDefinition = "text[]")
  private List<String> targetLocations = new ArrayList<>();

  @Column(name = "open_to_remote", nullable = false)
  private boolean openToRemote = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CandidateProfile() {}

  public CandidateProfile(UUID userId) {
    this.userId = userId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getHeadline() {
    return headline;
  }

  public void setHeadline(String headline) {
    this.headline = headline;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getLinkedinUrl() {
    return linkedinUrl;
  }

  public void setLinkedinUrl(String linkedinUrl) {
    this.linkedinUrl = linkedinUrl;
  }

  public String getGithubUrl() {
    return githubUrl;
  }

  public void setGithubUrl(String githubUrl) {
    this.githubUrl = githubUrl;
  }

  public String getPortfolioUrl() {
    return portfolioUrl;
  }

  public void setPortfolioUrl(String portfolioUrl) {
    this.portfolioUrl = portfolioUrl;
  }

  public List<String> getTargetRoles() {
    return targetRoles;
  }

  public void setTargetRoles(List<String> targetRoles) {
    this.targetRoles = targetRoles;
  }

  public List<String> getTargetLocations() {
    return targetLocations;
  }

  public void setTargetLocations(List<String> targetLocations) {
    this.targetLocations = targetLocations;
  }

  public boolean isOpenToRemote() {
    return openToRemote;
  }

  public void setOpenToRemote(boolean openToRemote) {
    this.openToRemote = openToRemote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
