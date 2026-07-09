package com.careercopilot.tailoring;

import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.EducationEntry;
import com.careercopilot.profile.EducationRepository;
import com.careercopilot.profile.HighlightType;
import com.careercopilot.profile.ProfileHighlight;
import com.careercopilot.profile.ProfileHighlightRepository;
import com.careercopilot.profile.SkillGroup;
import com.careercopilot.profile.SkillGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Injects structured profile facts (education, skill groups, achievements, certifications,
 * coursework) into the resume JSON payload AFTER the LLM call — the same deterministic, PII-safe
 * pattern as contactInfo. These sections never round-trip through the provider.
 */
@Component
public class ResumeSectionAssembler {

  private final EducationRepository educationRepository;
  private final SkillGroupRepository skillGroupRepository;
  private final ProfileHighlightRepository highlightRepository;
  private final ObjectMapper objectMapper;

  public ResumeSectionAssembler(
      EducationRepository educationRepository,
      SkillGroupRepository skillGroupRepository,
      ProfileHighlightRepository highlightRepository,
      ObjectMapper objectMapper) {
    this.educationRepository = educationRepository;
    this.skillGroupRepository = skillGroupRepository;
    this.highlightRepository = highlightRepository;
    this.objectMapper = objectMapper;
  }

  /** Adds render-ready section nodes to the payload; absent data adds nothing. */
  public void inject(ObjectNode payload, UUID userId, CandidateProfile profile) {
    injectEducation(payload, userId);
    injectSkillGroups(payload, userId);
    injectHighlights(payload, userId, HighlightType.SCHOLASTIC_ACHIEVEMENT, "achievements");
    injectHighlights(payload, userId, HighlightType.CERTIFICATION, "certifications");
    if (profile != null && !profile.getCoursework().isEmpty()) {
      payload.put("courseworkJoined", String.join(", ", profile.getCoursework()));
    }
  }

  private void injectEducation(ObjectNode payload, UUID userId) {
    List<EducationEntry> entries =
        educationRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
    if (entries.isEmpty()) {
      return;
    }
    ArrayNode education = objectMapper.createArrayNode();
    for (EducationEntry entry : entries) {
      ObjectNode node = education.addObject();
      node.put("degree", degreeLabel(entry));
      node.put("institution", entry.getInstitution());
      if (entry.getUniversity() != null
          && !entry.getUniversity().isBlank()
          && !entry.getUniversity().equalsIgnoreCase(entry.getInstitution())) {
        node.put("university", entry.getUniversity());
      }
      String yearRange = yearRange(entry);
      if (yearRange != null) {
        node.put("yearRange", yearRange);
      }
      String score = scoreLabel(entry);
      if (score != null) {
        node.put("score", score);
      }
    }
    payload.set("education", education);
  }

  private void injectSkillGroups(ObjectNode payload, UUID userId) {
    List<SkillGroup> groups = skillGroupRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
    if (groups.isEmpty()) {
      return;
    }
    ArrayNode skillGroups = objectMapper.createArrayNode();
    for (SkillGroup group : groups) {
      if (group.getSkills().isEmpty()) {
        continue;
      }
      ObjectNode node = skillGroups.addObject();
      node.put("name", group.getName());
      node.put("skillsJoined", String.join(", ", group.getSkills()));
    }
    if (!skillGroups.isEmpty()) {
      payload.set("skillGroups", skillGroups);
    }
  }

  private void injectHighlights(
      ObjectNode payload, UUID userId, HighlightType type, String fieldName) {
    List<ProfileHighlight> highlights =
        highlightRepository.findByUserIdAndTypeOrderByDisplayOrderAscIdAsc(userId, type);
    if (highlights.isEmpty()) {
      return;
    }
    ArrayNode items = objectMapper.createArrayNode();
    for (ProfileHighlight highlight : highlights) {
      ObjectNode node = items.addObject();
      node.put("text", highlight.getText());
      if (highlight.getYearLabel() != null && !highlight.getYearLabel().isBlank()) {
        node.put("yearLabel", highlight.getYearLabel());
      }
    }
    payload.set(fieldName, items);
  }

  private static String degreeLabel(EducationEntry entry) {
    if (entry.getSpecialization() != null && !entry.getSpecialization().isBlank()) {
      return entry.getDegree() + " (" + entry.getSpecialization() + ")";
    }
    return entry.getDegree();
  }

  private static String yearRange(EducationEntry entry) {
    if (entry.getStartYear() != null && entry.getEndYear() != null) {
      return entry.getStartYear() + " -- " + entry.getEndYear();
    }
    if (entry.getEndYear() != null) {
      return String.valueOf(entry.getEndYear());
    }
    return entry.getStartYear() != null ? String.valueOf(entry.getStartYear()) : null;
  }

  private static String scoreLabel(EducationEntry entry) {
    if (entry.getScoreValue() == null || entry.getScoreValue().isBlank()) {
      return null;
    }
    if (entry.getScoreType() == null) {
      return entry.getScoreValue();
    }
    return switch (entry.getScoreType()) {
      case CPI -> "CPI " + entry.getScoreValue();
      case PERCENTAGE -> entry.getScoreValue() + "%";
      case GRADE -> "Grade " + entry.getScoreValue();
    };
  }
}
