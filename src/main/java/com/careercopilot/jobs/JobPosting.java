package com.careercopilot.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_postings")
public class JobPosting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "job_source")
  private JobSource source;

  @Column(name = "external_id")
  private String externalId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String company;

  private String location;

  @Column(nullable = false)
  private boolean remote;

  @Column(name = "employment_type")
  private String employmentType;

  @Column(name = "salary_min")
  private BigDecimal salaryMin;

  @Column(name = "salary_max")
  private BigDecimal salaryMax;

  @Column(name = "salary_currency", length = 3)
  private String salaryCurrency;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private List<String> skills = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private List<String> responsibilities = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "matched_roles", nullable = false, columnDefinition = "text[]")
  private List<String> matchedRoles = new ArrayList<>();

  @Column(name = "full_text", nullable = false)
  private String fullText;

  private String url;

  @Column(name = "posted_at")
  private Instant postedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "pasted_by_user_id")
  private UUID pastedByUserId;

  @Column(name = "ingested_at", nullable = false)
  private Instant ingestedAt;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  protected JobPosting() {}

  public Long getId() {
    return id;
  }

  public JobSource getSource() {
    return source;
  }

  public void setSource(JobSource source) {
    this.source = source;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
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

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  public String getEmploymentType() {
    return employmentType;
  }

  public void setEmploymentType(String employmentType) {
    this.employmentType = employmentType;
  }

  public BigDecimal getSalaryMin() {
    return salaryMin;
  }

  public void setSalaryMin(BigDecimal salaryMin) {
    this.salaryMin = salaryMin;
  }

  public BigDecimal getSalaryMax() {
    return salaryMax;
  }

  public void setSalaryMax(BigDecimal salaryMax) {
    this.salaryMax = salaryMax;
  }

  public String getSalaryCurrency() {
    return salaryCurrency;
  }

  public void setSalaryCurrency(String salaryCurrency) {
    this.salaryCurrency = salaryCurrency;
  }

  public List<String> getSkills() {
    return skills;
  }

  public void setSkills(List<String> skills) {
    this.skills = skills;
  }

  public List<String> getResponsibilities() {
    return responsibilities;
  }

  public void setResponsibilities(List<String> responsibilities) {
    this.responsibilities = responsibilities;
  }

  public List<String> getMatchedRoles() {
    return matchedRoles;
  }

  public void setMatchedRoles(List<String> matchedRoles) {
    this.matchedRoles = matchedRoles;
  }

  public String getFullText() {
    return fullText;
  }

  public void setFullText(String fullText) {
    this.fullText = fullText;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public UUID getPastedByUserId() {
    return pastedByUserId;
  }

  public void setPastedByUserId(UUID pastedByUserId) {
    this.pastedByUserId = pastedByUserId;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }

  public void setIngestedAt(Instant ingestedAt) {
    this.ingestedAt = ingestedAt;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
