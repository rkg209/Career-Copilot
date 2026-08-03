package com.careercopilot.tailoring;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.CandidateProfileRepository;
import com.careercopilot.profile.ExperienceEntry;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tailoring.dto.CoverLetterRequest;
import com.careercopilot.tailoring.dto.CoverLetterResponse;
import com.careercopilot.tailoring.render.LatexRenderer;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.careercopilot.tailoring.render.RenderTimeoutException;
import com.careercopilot.tailoring.render.TemplateRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import org.springframework.stereotype.Service;

/** Cover-letter variant of the tailoring pipeline; same guardrails, same persist-then-render. */
@Service
public class CoverLetterService {

  private static final String DEFAULT_TEMPLATE = "cover-standard";
  private static final String DEFAULT_TONE = "professional";

  private final UserRepository userRepository;
  private final JobPostingRepository jobPostingRepository;
  private final CandidateProfileRepository profileRepository;
  private final TailoringService tailoringService;
  private final LlmGateway llmGateway;
  private final PromptInjectionAdvisor promptInjectionAdvisor;
  private final TemplateRegistry templateRegistry;
  private final LatexRenderer latexRenderer;
  private final CoverLetterRepository coverLetterRepository;

  public CoverLetterService(
      UserRepository userRepository,
      JobPostingRepository jobPostingRepository,
      CandidateProfileRepository profileRepository,
      TailoringService tailoringService,
      LlmGateway llmGateway,
      PromptInjectionAdvisor promptInjectionAdvisor,
      TemplateRegistry templateRegistry,
      LatexRenderer latexRenderer,
      CoverLetterRepository coverLetterRepository) {
    this.userRepository = userRepository;
    this.jobPostingRepository = jobPostingRepository;
    this.profileRepository = profileRepository;
    this.tailoringService = tailoringService;
    this.llmGateway = llmGateway;
    this.promptInjectionAdvisor = promptInjectionAdvisor;
    this.templateRegistry = templateRegistry;
    this.latexRenderer = latexRenderer;
    this.coverLetterRepository = coverLetterRepository;
  }

  @Timed(
      value = "tailoring.coverLetter",
      percentiles = {0.5, 0.99})
  public CoverLetterResponse generate(String email, CoverLetterRequest request) {
    User user = resolveUser(email);
    String templateId = request.templateId() != null ? request.templateId() : DEFAULT_TEMPLATE;
    TemplateRegistry.Template template =
        templateRegistry.require(templateId, TemplateRegistry.Kind.COVER_LETTER);
    JobPosting job =
        jobPostingRepository
            .findById(request.jobPostingId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Job posting not found: " + request.jobPostingId()));
    if (request.resumeVersionId() != null) {
      // Validates existence and ownership; 404/403 like the resume endpoints.
      tailoringService.ownedResume(email, request.resumeVersionId());
    }
    CandidateProfile profile = profileRepository.findByUserId(user.getId()).orElse(null);
    List<ExperienceEntry> entries = tailoringService.retrieve(user.getId(), job);
    String candidateName = TailoringService.displayName(profile, user);
    String tone =
        request.tone() != null && !request.tone().isBlank() ? request.tone() : DEFAULT_TONE;
    String fencedJd = promptInjectionAdvisor.fence(job.getFullText());

    LlmGateway.LlmJsonResult result =
        llmGateway.callForJson(
            user.getId(),
            LlmTask.COVER_LETTER,
            TailoringPrompts.COVER_LETTER_SYSTEM_PROMPT,
            TailoringPrompts.coverLetterUserPrompt(
                candidateName, profile, job, fencedJd, entries, tone),
            "cover-letter-json-payload",
            TailoringService.groundingContext(entries));

    ObjectNode payload = (ObjectNode) result.payload();
    payload.put("candidateName", candidateName);

    CoverLetter saved =
        coverLetterRepository.save(
            new CoverLetter(
                user.getId(),
                job.getId(),
                request.resumeVersionId(),
                template.id(),
                payload.toString(),
                result.modelUsed(),
                result.totalTokens()));

    renderInto(saved, template);
    return toResponse(saved);
  }

  /** Returns PDF bytes, re-rendering from the persisted JSON when bytes are missing. */
  public byte[] getPdf(String email, Long id) {
    CoverLetter letter = ownedCoverLetter(email, id);
    if (letter.getPdfBytes() != null) {
      return letter.getPdfBytes();
    }
    TemplateRegistry.Template template =
        templateRegistry.require(letter.getTemplateId(), TemplateRegistry.Kind.COVER_LETTER);
    renderInto(letter, template);
    return letter.getPdfBytes();
  }

  private CoverLetter ownedCoverLetter(String email, Long id) {
    User user = resolveUser(email);
    CoverLetter letter =
        coverLetterRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cover letter not found: " + id));
    if (!letter.getUserId().equals(user.getId())) {
      throw new ForbiddenException("Cover letter belongs to another user");
    }
    return letter;
  }

  private void renderInto(CoverLetter letter, TemplateRegistry.Template template) {
    try {
      byte[] pdf = latexRenderer.render(template, letter.getJsonPayload());
      letter.setPdfBytes(pdf);
      letter.setRenderFailed(false);
      letter.setRenderError(null);
      coverLetterRepository.save(letter);
    } catch (RenderTimeoutException | RenderFailedException e) {
      letter.setRenderFailed(true);
      letter.setRenderError(e.getMessage());
      coverLetterRepository.save(letter);
      String suffix =
          " (cover letter JSON saved as id %d — retry the PDF endpoint later)"
              .formatted(letter.getId());
      if (e instanceof RenderTimeoutException) {
        throw new RenderTimeoutException(e.getMessage() + suffix);
      }
      throw new RenderFailedException(e.getMessage() + suffix, e);
    }
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }

  private static CoverLetterResponse toResponse(CoverLetter letter) {
    String status =
        letter.getPdfBytes() != null
            ? "RENDERED"
            : (letter.isRenderFailed() ? "FAILED" : "PENDING");
    return new CoverLetterResponse(
        letter.getId(),
        letter.getTemplateId(),
        letter.getJobPostingId(),
        letter.getResumeVersionId(),
        letter.getLlmModel(),
        letter.getTokenCount(),
        status,
        letter.getCreatedAt());
  }
}
