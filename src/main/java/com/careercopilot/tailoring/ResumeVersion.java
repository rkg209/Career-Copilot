package com.careercopilot.tailoring;

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
@Table(name = "resume_versions")
public class ResumeVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "job_posting_id", nullable = false)
  private Long jobPostingId;

  @Column(name = "template_id", nullable = false)
  private String templateId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "json_payload", nullable = false, columnDefinition = "jsonb")
  private String jsonPayload;

  @Column(name = "pdf_bytes")
  private byte[] pdfBytes;

  @Column(name = "llm_model", nullable = false)
  private String llmModel;

  @Column(name = "token_count", nullable = false)
  private int tokenCount;

  @Column(name = "render_failed", nullable = false)
  private boolean renderFailed;

  @Column(name = "render_error")
  private String renderError;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeVersion() {}

  public ResumeVersion(
      UUID userId,
      Long jobPostingId,
      String templateId,
      String jsonPayload,
      String llmModel,
      int tokenCount) {
    this.userId = userId;
    this.jobPostingId = jobPostingId;
    this.templateId = templateId;
    this.jsonPayload = jsonPayload;
    this.llmModel = llmModel;
    this.tokenCount = tokenCount;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getJobPostingId() {
    return jobPostingId;
  }

  public String getTemplateId() {
    return templateId;
  }

  public String getJsonPayload() {
    return jsonPayload;
  }

  public byte[] getPdfBytes() {
    return pdfBytes;
  }

  public void setPdfBytes(byte[] pdfBytes) {
    this.pdfBytes = pdfBytes;
  }

  public String getLlmModel() {
    return llmModel;
  }

  public int getTokenCount() {
    return tokenCount;
  }

  public boolean isRenderFailed() {
    return renderFailed;
  }

  public void setRenderFailed(boolean renderFailed) {
    this.renderFailed = renderFailed;
  }

  public String getRenderError() {
    return renderError;
  }

  public void setRenderError(String renderError) {
    this.renderError = renderError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
