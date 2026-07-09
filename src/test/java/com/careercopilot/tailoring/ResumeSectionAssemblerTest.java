package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.EducationEntry;
import com.careercopilot.profile.EducationRepository;
import com.careercopilot.profile.EducationScoreType;
import com.careercopilot.profile.HighlightType;
import com.careercopilot.profile.ProfileHighlight;
import com.careercopilot.profile.ProfileHighlightRepository;
import com.careercopilot.profile.SkillGroup;
import com.careercopilot.profile.SkillGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeSectionAssemblerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private final ObjectMapper mapper = new ObjectMapper();
  private EducationRepository educationRepository;
  private SkillGroupRepository skillGroupRepository;
  private ProfileHighlightRepository highlightRepository;
  private ResumeSectionAssembler assembler;

  @BeforeEach
  void setUp() {
    educationRepository = mock(EducationRepository.class);
    skillGroupRepository = mock(SkillGroupRepository.class);
    highlightRepository = mock(ProfileHighlightRepository.class);
    assembler =
        new ResumeSectionAssembler(
            educationRepository, skillGroupRepository, highlightRepository, mapper);
    when(educationRepository.findByUserIdOrderByDisplayOrderAscIdAsc(any())).thenReturn(List.of());
    when(skillGroupRepository.findByUserIdOrderByDisplayOrderAscIdAsc(any())).thenReturn(List.of());
    when(highlightRepository.findByUserIdAndTypeOrderByDisplayOrderAscIdAsc(any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void inject_emptyProfile_addsNothing() {
    ObjectNode payload = mapper.createObjectNode();
    assembler.inject(payload, USER_ID, null);
    assertThat(payload.has("education")).isFalse();
    assertThat(payload.has("skillGroups")).isFalse();
    assertThat(payload.has("achievements")).isFalse();
    assertThat(payload.has("certifications")).isFalse();
    assertThat(payload.has("courseworkJoined")).isFalse();
  }

  @Test
  void inject_education_producesRenderReadyFields() {
    EducationEntry entry = new EducationEntry(USER_ID);
    entry.setDegree("B.Tech.");
    entry.setSpecialization("Computer Science");
    entry.setInstitution("IIT Bombay");
    entry.setStartYear(2022);
    entry.setEndYear(2026);
    entry.setScoreType(EducationScoreType.CPI);
    entry.setScoreValue("9.24");
    when(educationRepository.findByUserIdOrderByDisplayOrderAscIdAsc(USER_ID))
        .thenReturn(List.of(entry));

    ObjectNode payload = mapper.createObjectNode();
    assembler.inject(payload, USER_ID, null);

    ObjectNode education = (ObjectNode) payload.get("education").get(0);
    assertThat(education.get("degree").asText()).isEqualTo("B.Tech. (Computer Science)");
    assertThat(education.get("institution").asText()).isEqualTo("IIT Bombay");
    assertThat(education.get("yearRange").asText()).isEqualTo("2022 -- 2026");
    assertThat(education.get("score").asText()).isEqualTo("CPI 9.24");
  }

  @Test
  void inject_percentageScore_formatsWithPercentSign() {
    EducationEntry entry = new EducationEntry(USER_ID);
    entry.setDegree("Class XII");
    entry.setInstitution("SMP School");
    entry.setScoreType(EducationScoreType.PERCENTAGE);
    entry.setScoreValue("94.20");
    when(educationRepository.findByUserIdOrderByDisplayOrderAscIdAsc(USER_ID))
        .thenReturn(List.of(entry));

    ObjectNode payload = mapper.createObjectNode();
    assembler.inject(payload, USER_ID, null);

    assertThat(payload.get("education").get(0).get("score").asText()).isEqualTo("94.20%");
  }

  @Test
  void inject_skillGroupsAndHighlightsAndCoursework() {
    SkillGroup group = new SkillGroup(USER_ID);
    group.setName("Programming Languages");
    group.setSkills(List.of("C++", "Python"));
    when(skillGroupRepository.findByUserIdOrderByDisplayOrderAscIdAsc(USER_ID))
        .thenReturn(List.of(group));

    ProfileHighlight achievement = new ProfileHighlight(USER_ID);
    achievement.setType(HighlightType.SCHOLASTIC_ACHIEVEMENT);
    achievement.setText("JEE Advanced 97 percentile");
    achievement.setYearLabel("2022");
    when(highlightRepository.findByUserIdAndTypeOrderByDisplayOrderAscIdAsc(
            eq(USER_ID), eq(HighlightType.SCHOLASTIC_ACHIEVEMENT)))
        .thenReturn(List.of(achievement));

    CandidateProfile profile = new CandidateProfile(USER_ID);
    profile.setCoursework(List.of("Operating Systems", "Databases"));

    ObjectNode payload = mapper.createObjectNode();
    assembler.inject(payload, USER_ID, profile);

    assertThat(payload.get("skillGroups").get(0).get("skillsJoined").asText())
        .isEqualTo("C++, Python");
    assertThat(payload.get("achievements").get(0).get("yearLabel").asText()).isEqualTo("2022");
    assertThat(payload.has("certifications")).isFalse();
    assertThat(payload.get("courseworkJoined").asText()).isEqualTo("Operating Systems, Databases");
  }
}
