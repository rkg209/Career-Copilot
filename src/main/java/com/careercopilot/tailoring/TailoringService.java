package com.careercopilot.tailoring;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.CandidateProfileRepository;
import com.careercopilot.profile.ExperienceEntry;
import com.careercopilot.profile.ExperienceRepository;
import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.shared.guardrail.GroundingValidator;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tailoring.dto.ResumeDetailResponse;
import com.careercopilot.tailoring.dto.ResumeResponse;
import com.careercopilot.tailoring.dto.TailorRequest;
import com.careercopilot.tailoring.render.LatexRenderer;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.careercopilot.tailoring.render.RenderTimeoutException;
import com.careercopilot.tailoring.render.TemplateRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the tailoring pipeline: retrieve (RAG) → LLM (guardrailed, JSON only) → persist JSON
 * → deterministic render. Deliberately NOT class-level transactional: the ResumeVersion with its
 * JSON payload is committed before rendering starts, so a render failure never loses the LLM output
 * (NFR-3.1).
 */
@Service
public class TailoringService {

  private final UserRepository userRepository;
  private final JobPostingRepository jobPostingRepository;
  private final CandidateProfileRepository profileRepository;
  private final ExperienceRepository experienceRepository;
  private final EmbeddingGateway embeddingGateway;
  private final LlmGateway llmGateway;
  private final PromptInjectionAdvisor promptInjectionAdvisor;
  private final TemplateRegistry templateRegistry;
  private final LatexRenderer latexRenderer;
  private final ResumeVersionRepository resumeVersionRepository;
  private final ResumeSectionAssembler sectionAssembler;
  private final ObjectMapper objectMapper;
  private final int retrievalTopK;

  public TailoringService(
      UserRepository userRepository,
      JobPostingRepository jobPostingRepository,
      CandidateProfileRepository profileRepository,
      ExperienceRepository experienceRepository,
      EmbeddingGateway embeddingGateway,
      LlmGateway llmGateway,
      PromptInjectionAdvisor promptInjectionAdvisor,
      TemplateRegistry templateRegistry,
      LatexRenderer latexRenderer,
      ResumeVersionRepository resumeVersionRepository,
      ResumeSectionAssembler sectionAssembler,
      ObjectMapper objectMapper,
      @Value("${tailoring.retrieval-top-k}") int retrievalTopK) {
    this.userRepository = userRepository;
    this.jobPostingRepository = jobPostingRepository;
    this.profileRepository = profileRepository;
    this.experienceRepository = experienceRepository;
    this.embeddingGateway = embeddingGateway;
    this.llmGateway = llmGateway;
    this.promptInjectionAdvisor = promptInjectionAdvisor;
    this.templateRegistry = templateRegistry;
    this.latexRenderer = latexRenderer;
    this.resumeVersionRepository = resumeVersionRepository;
    this.sectionAssembler = sectionAssembler;
    this.objectMapper = objectMapper;
    this.retrievalTopK = retrievalTopK;
  }

  public ResumeResponse tailor(String email, TailorRequest request) {
    User user = resolveUser(email);
    TemplateRegistry.Template template =
        templateRegistry.require(request.templateId(), TemplateRegistry.Kind.RESUME);
    JobPosting job =
        jobPostingRepository
            .findById(request.jobPostingId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Job posting not found: " + request.jobPostingId()));
    CandidateProfile profile = profileRepository.findByUserId(user.getId()).orElse(null);

    List<ExperienceEntry> entries = retrieve(user.getId(), job);
    String candidateName = displayName(profile, user);
    String fencedJd = promptInjectionAdvisor.fence(job.getFullText());

    LlmGateway.LlmJsonResult result =
        llmGateway.callForJson(
            user.getId(),
            LlmTask.TAILORING,
            TailoringPrompts.RESUME_SYSTEM_PROMPT,
            TailoringPrompts.resumeUserPrompt(candidateName, profile, job, fencedJd, entries),
            "resume-json-payload",
            groundingContext(entries));

    // Contact info and structured profile facts (education, skill groups, achievements,
    // certifications, coursework) are injected deterministically after the LLM call — they
    // never round-trip through the provider.
    ObjectNode payload = (ObjectNode) result.payload();
    payload.put("candidateName", candidateName);
    payload.set("contactInfo", contactInfo(profile, user));
    sectionAssembler.inject(payload, user.getId(), profile);

    ResumeVersion saved =
        resumeVersionRepository.save(
            new ResumeVersion(
                user.getId(),
                job.getId(),
                template.id(),
                payload.toString(),
                result.modelUsed(),
                result.totalTokens()));

    renderInto(saved, template);
    return toResponse(saved);
  }

  public PageResponse<ResumeResponse> list(String email, int page, int size) {
    User user = resolveUser(email);
    Page<ResumeVersion> versions =
        resumeVersionRepository.findByUserId(
            user.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    return PageResponse.of(
        versions, versions.getContent().stream().map(TailoringService::toResponse).toList());
  }

  public ResumeDetailResponse get(String email, Long id) {
    ResumeVersion version = ownedResume(email, id);
    JsonNode payload;
    try {
      payload = objectMapper.readTree(version.getJsonPayload());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored resume payload is corrupt for id " + id, e);
    }
    return new ResumeDetailResponse(
        version.getId(),
        version.getTemplateId(),
        version.getJobPostingId(),
        version.getLlmModel(),
        version.getTokenCount(),
        renderStatus(version),
        version.getRenderError(),
        payload,
        version.getCreatedAt());
  }

  /** Returns PDF bytes, re-rendering from the persisted JSON when bytes are missing. */
  public byte[] getPdf(String email, Long id) {
    ResumeVersion version = ownedResume(email, id);
    if (version.getPdfBytes() != null) {
      return version.getPdfBytes();
    }
    TemplateRegistry.Template template =
        templateRegistry.require(version.getTemplateId(), TemplateRegistry.Kind.RESUME);
    renderInto(version, template);
    return version.getPdfBytes();
  }

  ResumeVersion ownedResume(String email, Long id) {
    User user = resolveUser(email);
    ResumeVersion version =
        resumeVersionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Resume version not found: " + id));
    if (!version.getUserId().equals(user.getId())) {
      throw new ForbiddenException("Resume version belongs to another user");
    }
    return version;
  }

  List<ExperienceEntry> retrieve(UUID userId, JobPosting job) {
    float[] jdVector = embeddingGateway.embed(userId, LlmTask.EMBEDDING, job.getFullText());
    List<ExperienceEntry> entries =
        experienceRepository.findTopKByVector(userId, jdVector, Limit.of(retrievalTopK));
    if (entries.isEmpty()) {
      throw new InsufficientExperienceException(
          "No embedded experience entries found. Add experience to your profile (and allow"
              + " embedding to complete) before tailoring.");
    }
    return entries;
  }

  static GroundingValidator.GroundingContext groundingContext(List<ExperienceEntry> entries) {
    Map<Long, GroundingValidator.GroundingContext.Source> sources = new HashMap<>();
    for (ExperienceEntry entry : entries) {
      sources.put(
          entry.getId(),
          new GroundingValidator.GroundingContext.Source(entry.getCompany(), entry.getRole()));
    }
    return new GroundingValidator.GroundingContext(sources);
  }

  static String displayName(CandidateProfile profile, User user) {
    if (profile != null && profile.getFullName() != null && !profile.getFullName().isBlank()) {
      return profile.getFullName();
    }
    String local = user.getEmail().split("@")[0];
    return local.isBlank() ? "Candidate" : local;
  }

  ObjectNode contactInfo(CandidateProfile profile, User user) {
    ObjectNode contact = objectMapper.createObjectNode();
    contact.put("email", user.getEmail());
    if (profile != null) {
      putIfPresent(contact, "phone", profile.getPhone());
      putIfPresent(contact, "location", profile.getLocation());
      putIfPresent(contact, "linkedinUrl", profile.getLinkedinUrl());
      putIfPresent(contact, "githubUrl", profile.getGithubUrl());
      putIfPresent(contact, "portfolioUrl", profile.getPortfolioUrl());
    }
    return contact;
  }

  private static void putIfPresent(ObjectNode node, String field, String value) {
    if (value != null && !value.isBlank()) {
      node.put(field, value);
    }
  }

  /**
   * Renders and persists the outcome. The entity is already committed with its JSON payload; on
   * failure we record it and rethrow so the API surfaces 503/500 with the persisted id.
   */
  private void renderInto(ResumeVersion version, TemplateRegistry.Template template) {
    try {
      byte[] pdf = latexRenderer.render(template, version.getJsonPayload());
      version.setPdfBytes(pdf);
      version.setRenderFailed(false);
      version.setRenderError(null);
      resumeVersionRepository.save(version);
    } catch (RenderTimeoutException | RenderFailedException e) {
      version.setRenderFailed(true);
      version.setRenderError(e.getMessage());
      resumeVersionRepository.save(version);
      rethrowWithId(e, version.getId());
    }
  }

  private static void rethrowWithId(RuntimeException e, Long resumeVersionId) {
    String suffix =
        " (resume JSON saved as version %d — retry the PDF endpoint later)"
            .formatted(resumeVersionId);
    if (e instanceof RenderTimeoutException) {
      throw new RenderTimeoutException(e.getMessage() + suffix);
    }
    throw new RenderFailedException(e.getMessage() + suffix, e);
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }

  static ResumeResponse toResponse(ResumeVersion version) {
    return new ResumeResponse(
        version.getId(),
        version.getTemplateId(),
        version.getJobPostingId(),
        version.getLlmModel(),
        version.getTokenCount(),
        renderStatus(version),
        version.getCreatedAt());
  }

  static String renderStatus(ResumeVersion version) {
    if (version.getPdfBytes() != null) {
      return "RENDERED";
    }
    return version.isRenderFailed() ? "FAILED" : "PENDING";
  }
}
