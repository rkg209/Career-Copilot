package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.profile.CandidateProfileRepository;
import com.careercopilot.profile.ExperienceEntry;
import com.careercopilot.profile.ExperienceRepository;
import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tailoring.dto.TailorRequest;
import com.careercopilot.tailoring.render.LatexRenderer;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.careercopilot.tailoring.render.TemplateRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

/** Orchestration unit test with all collaborators mocked. */
class TailoringServiceTest {

  private UserRepository userRepository;
  private JobPostingRepository jobPostingRepository;
  private CandidateProfileRepository profileRepository;
  private ExperienceRepository experienceRepository;
  private EmbeddingGateway embeddingGateway;
  private LlmGateway llmGateway;
  private LatexRenderer latexRenderer;
  private ResumeVersionRepository resumeVersionRepository;
  private TailoringService service;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    jobPostingRepository = mock(JobPostingRepository.class);
    profileRepository = mock(CandidateProfileRepository.class);
    experienceRepository = mock(ExperienceRepository.class);
    embeddingGateway = mock(EmbeddingGateway.class);
    llmGateway = mock(LlmGateway.class);
    latexRenderer = mock(LatexRenderer.class);
    resumeVersionRepository = mock(ResumeVersionRepository.class);
    service =
        new TailoringService(
            userRepository,
            jobPostingRepository,
            profileRepository,
            experienceRepository,
            embeddingGateway,
            llmGateway,
            new PromptInjectionAdvisor(),
            new TemplateRegistry(),
            latexRenderer,
            resumeVersionRepository,
            mapper,
            6);
  }

  private User seedUser() {
    User user = new User("jane@example.com", "hash");
    when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
    return user;
  }

  private void seedJob() {
    var job = TailoringTestFixtures.jobPosting();
    when(jobPostingRepository.findById(42L)).thenReturn(Optional.of(job));
  }

  private void seedPipelineUpToLlm() throws Exception {
    seedUser();
    seedJob();
    when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
    when(embeddingGateway.embed(any(), any(), anyString())).thenReturn(new float[768]);
    ExperienceEntry entry = TailoringTestFixtures.experienceEntry();
    when(experienceRepository.findTopKByVector(any(), any(), any(Limit.class)))
        .thenReturn(List.of(entry));
    var payload =
        mapper.readTree(
            """
            {"candidateName":"x",
             "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["b"]}],
             "skills":["Java"]}""");
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(payload, "test-model", 1234));
    when(resumeVersionRepository.save(any(ResumeVersion.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void tailor_persistsJsonBeforeRender_andStoresPdfOnSuccess() throws Exception {
    seedPipelineUpToLlm();
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-fake".getBytes());

    var response = service.tailor("jane@example.com", new TailorRequest(42L, "classic-ats"));

    assertThat(response.renderStatus()).isEqualTo("RENDERED");
    assertThat(response.llmModel()).isEqualTo("test-model");
    assertThat(response.tokenCount()).isEqualTo(1234);
    // First save persists the JSON payload, second save attaches the PDF.
    ArgumentCaptor<ResumeVersion> saves = ArgumentCaptor.forClass(ResumeVersion.class);
    verify(resumeVersionRepository, times(2)).save(saves.capture());
    assertThat(saves.getAllValues().get(0).getJsonPayload()).contains("sourceEntryId");
  }

  @Test
  void tailor_keepsJsonAndMarksFailure_whenRenderFails() throws Exception {
    seedPipelineUpToLlm();
    when(latexRenderer.render(any(), anyString())).thenThrow(new RenderFailedException("boom"));

    assertThatThrownBy(
            () -> service.tailor("jane@example.com", new TailorRequest(42L, "classic-ats")))
        .isInstanceOf(RenderFailedException.class);

    ArgumentCaptor<ResumeVersion> saves = ArgumentCaptor.forClass(ResumeVersion.class);
    verify(resumeVersionRepository, times(2)).save(saves.capture());
    ResumeVersion afterFailure = saves.getAllValues().get(1);
    assertThat(afterFailure.isRenderFailed()).isTrue();
    assertThat(afterFailure.getRenderError()).contains("boom");
    assertThat(afterFailure.getJsonPayload()).contains("sourceEntryId");
    assertThat(afterFailure.getPdfBytes()).isNull();
  }

  @Test
  void tailor_emptyCorpus_throwsInsufficientExperience() {
    seedUser();
    seedJob();
    when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
    when(embeddingGateway.embed(any(), any(), anyString())).thenReturn(new float[768]);
    when(experienceRepository.findTopKByVector(any(), any(), any(Limit.class)))
        .thenReturn(List.of());

    assertThatThrownBy(
            () -> service.tailor("jane@example.com", new TailorRequest(42L, "classic-ats")))
        .isInstanceOf(InsufficientExperienceException.class);
  }

  @Test
  void tailor_unknownTemplate_failsBeforeAnyLlmWork() {
    seedUser();
    assertThatThrownBy(() -> service.tailor("jane@example.com", new TailorRequest(42L, "nope")))
        .hasMessageContaining("nope");
    verify(llmGateway, times(0))
        .callForJson(any(), any(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void tailor_fencesJobDescriptionBeforePromptAssembly() throws Exception {
    seedPipelineUpToLlm();
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-fake".getBytes());

    service.tailor("jane@example.com", new TailorRequest(42L, "classic-ats"));

    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmGateway)
        .callForJson(
            any(), any(), anyString(), userPrompt.capture(), eq("resume-json-payload"), any());
    assertThat(userPrompt.getValue())
        .contains("--- BEGIN JOB DESCRIPTION")
        .contains("[CONTENT_REMOVED]")
        .doesNotContainIgnoringCase("ignore all instructions");
  }
}
