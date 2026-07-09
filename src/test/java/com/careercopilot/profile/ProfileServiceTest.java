package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.profile.dto.ExperienceRequest;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.ProfileDto;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.ForbiddenException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProfileServiceTest {

  private static final String EMAIL = "user@example.com";
  private static final UUID USER_ID = UUID.randomUUID();

  private CandidateProfileRepository profileRepository;
  private ExperienceRepository experienceRepository;
  private EducationRepository educationRepository;
  private SkillGroupRepository skillGroupRepository;
  private ProfileHighlightRepository highlightRepository;
  private UserRepository userRepository;
  private ApplicationEventPublisher eventPublisher;
  private ProfileService service;

  @BeforeEach
  void setUp() {
    profileRepository = mock(CandidateProfileRepository.class);
    experienceRepository = mock(ExperienceRepository.class);
    educationRepository = mock(EducationRepository.class);
    skillGroupRepository = mock(SkillGroupRepository.class);
    highlightRepository = mock(ProfileHighlightRepository.class);
    userRepository = mock(UserRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    service =
        new ProfileService(
            profileRepository,
            experienceRepository,
            educationRepository,
            skillGroupRepository,
            highlightRepository,
            userRepository,
            eventPublisher);

    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
  }

  @Test
  void getProfile_returnsEmptyShellWhenNoneExists() {
    when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    ProfileDto dto = service.getProfile(EMAIL);

    assertThat(dto.headline()).isNull();
    assertThat(dto.targetRoles()).isEmpty();
    assertThat(dto.openToRemote()).isTrue();
  }

  @Test
  void upsertProfile_createsOnFirstCall() {
    when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(i -> i.getArgument(0));

    ProfileDto saved =
        service.upsertProfile(
            EMAIL,
            new ProfileDto(
                null,
                null,
                "Backend Engineer",
                null,
                null,
                null,
                null,
                null,
                List.of("SDE"),
                null,
                null,
                null,
                null));

    assertThat(saved.headline()).isEqualTo("Backend Engineer");
    assertThat(saved.targetRoles()).containsExactly("SDE");
    assertThat(saved.openToRemote()).isTrue();
    verify(profileRepository).save(any(CandidateProfile.class));
  }

  @Test
  void addExperience_savesAndPublishesEvent() {
    when(experienceRepository.save(any(ExperienceEntry.class)))
        .thenAnswer(i -> withId(i.getArgument(0), 42L));

    ExperienceResponse response = service.addExperience(EMAIL, validRequest());

    assertThat(response.embeddingStatus()).isEqualTo("PENDING");
    verify(eventPublisher).publishEvent(new ExperienceChangedEvent(42L));
  }

  @Test
  void updateExperience_unchangedText_doesNotRepublish() {
    ExperienceEntry entry = withId(new ExperienceEntry(USER_ID), 42L);
    applyValidRequest(entry);
    String hash = ExperienceText.sha256(ExperienceText.build(entry));
    entry.setTextHash(hash);
    entry.setEmbedding(new float[] {1f});
    when(experienceRepository.findById(42L)).thenReturn(Optional.of(entry));
    when(experienceRepository.save(any(ExperienceEntry.class))).thenAnswer(i -> i.getArgument(0));

    service.updateExperience(EMAIL, 42L, validRequest());

    verify(eventPublisher, never()).publishEvent(any());
    assertThat(entry.getEmbedding()).isNotNull();
  }

  @Test
  void updateExperience_changedText_clearsEmbeddingAndPublishes() {
    ExperienceEntry entry = withId(new ExperienceEntry(USER_ID), 42L);
    applyValidRequest(entry);
    entry.setTextHash("old-hash");
    entry.setEmbedding(new float[] {1f});
    when(experienceRepository.findById(42L)).thenReturn(Optional.of(entry));
    when(experienceRepository.save(any(ExperienceEntry.class))).thenAnswer(i -> i.getArgument(0));

    ExperienceResponse response = service.updateExperience(EMAIL, 42L, validRequest());

    assertThat(response.embeddingStatus()).isEqualTo("PENDING");
    assertThat(entry.getEmbedding()).isNull();
    verify(eventPublisher).publishEvent(new ExperienceChangedEvent(42L));
  }

  @Test
  void updateExperience_otherUsersEntry_throwsForbidden() {
    ExperienceEntry entry = withId(new ExperienceEntry(UUID.randomUUID()), 42L);
    when(experienceRepository.findById(42L)).thenReturn(Optional.of(entry));

    assertThatThrownBy(() -> service.updateExperience(EMAIL, 42L, validRequest()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void addExperience_currentRoleWithEndDate_throwsBadRequest() {
    ExperienceRequest bad =
        new ExperienceRequest(
            "SDE",
            "Acme",
            null,
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2024, 1, 1),
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.addExperience(EMAIL, bad))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void addExperience_endBeforeStart_throwsBadRequest() {
    ExperienceRequest bad =
        new ExperienceRequest(
            "SDE",
            "Acme",
            null,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2023, 1, 1),
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.addExperience(EMAIL, bad))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void addExperience_projectTypeWithoutProjectEntry_throwsBadRequest() {
    ExperienceRequest bad =
        new ExperienceRequest(
            "SDE",
            "Acme",
            null,
            LocalDate.of(2023, 1, 1),
            null,
            false,
            null,
            null,
            null,
            CorpusEntryType.WORK,
            ProjectType.SELF,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.addExperience(EMAIL, bad))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void addExperience_projectEntry_persistsTypedFields() {
    when(experienceRepository.save(any(ExperienceEntry.class)))
        .thenAnswer(i -> withId(i.getArgument(0), 7L));
    ExperienceRequest project =
        new ExperienceRequest(
            "Quadcopter control",
            "Inter-IIT Team",
            null,
            LocalDate.of(2024, 10, 1),
            null,
            false,
            null,
            null,
            null,
            CorpusEntryType.PROJECT,
            ProjectType.COURSE,
            "Prof. Vachhani",
            "Inter-IIT Tech Meet 2024",
            "https://github.com/x/y");

    ExperienceResponse response = service.addExperience(EMAIL, project);

    assertThat(response.entryType()).isEqualTo(CorpusEntryType.PROJECT);
    assertThat(response.projectType()).isEqualTo(ProjectType.COURSE);
    assertThat(response.guide()).isEqualTo("Prof. Vachhani");
  }

  @Test
  void addExperience_noEntryType_defaultsToWork() {
    when(experienceRepository.save(any(ExperienceEntry.class)))
        .thenAnswer(i -> withId(i.getArgument(0), 8L));

    ExperienceResponse response = service.addExperience(EMAIL, validRequest());

    assertThat(response.entryType()).isEqualTo(CorpusEntryType.WORK);
  }

  @Test
  void replaceSkillGroups_duplicateName_throwsBadRequest() {
    List<com.careercopilot.profile.dto.SkillGroupDto> groups =
        List.of(
            new com.careercopilot.profile.dto.SkillGroupDto(null, "Languages", List.of("C"), 0),
            new com.careercopilot.profile.dto.SkillGroupDto(null, "languages", List.of("Go"), 1));

    assertThatThrownBy(() -> service.replaceSkillGroups(EMAIL, groups))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void addEducation_scoreValueWithoutType_throwsBadRequest() {
    com.careercopilot.profile.dto.EducationRequest bad =
        new com.careercopilot.profile.dto.EducationRequest(
            "B.Tech.", "IIT Bombay", null, null, null, null, null, "9.24", null);

    assertThatThrownBy(() -> service.addEducation(EMAIL, bad))
        .isInstanceOf(BadRequestException.class);
  }

  private static ExperienceRequest validRequest() {
    return new ExperienceRequest(
        "Backend Engineer",
        "Acme",
        "Bengaluru",
        LocalDate.of(2023, 1, 1),
        null,
        true,
        "Built APIs",
        List.of("Cut latency 40%"),
        List.of("Java", "Postgres"),
        null,
        null,
        null,
        null,
        null);
  }

  private static void applyValidRequest(ExperienceEntry entry) {
    ExperienceRequest req = validRequest();
    entry.setRole(req.role());
    entry.setCompany(req.company());
    entry.setLocation(req.location());
    entry.setStartDate(req.startDate());
    entry.setEndDate(req.endDate());
    entry.setCurrent(Boolean.TRUE.equals(req.isCurrent()));
    entry.setDescription(req.description());
    entry.setAchievements(req.achievements());
    entry.setSkillsUsed(req.skillsUsed());
  }

  private static ExperienceEntry withId(ExperienceEntry entry, Long id) {
    try {
      Field field = ExperienceEntry.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entry, id);
      return entry;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
