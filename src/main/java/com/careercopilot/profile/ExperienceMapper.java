package com.careercopilot.profile;

import com.careercopilot.profile.dto.EducationResponse;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.HighlightResponse;
import com.careercopilot.profile.dto.ProfileDto;
import com.careercopilot.profile.dto.SkillGroupDto;
import java.util.List;

final class ExperienceMapper {

  private ExperienceMapper() {}

  static ExperienceResponse toResponse(ExperienceEntry entry) {
    return new ExperienceResponse(
        entry.getId(),
        entry.getRole(),
        entry.getCompany(),
        entry.getLocation(),
        entry.getStartDate(),
        entry.getEndDate(),
        entry.isCurrent(),
        entry.getDescription(),
        entry.getAchievements(),
        entry.getSkillsUsed(),
        entry.getEntryType(),
        entry.getProjectType(),
        entry.getGuide(),
        entry.getContext(),
        entry.getUrl(),
        entry.getEmbeddedAt() != null ? "EMBEDDED" : "PENDING",
        entry.getCreatedAt(),
        entry.getUpdatedAt());
  }

  static ProfileDto toDto(CandidateProfile profile) {
    return new ProfileDto(
        profile.getFullName(),
        profile.getPhone(),
        profile.getHeadline(),
        profile.getSummary(),
        profile.getLocation(),
        profile.getLinkedinUrl(),
        profile.getGithubUrl(),
        profile.getPortfolioUrl(),
        profile.getTargetRoles(),
        profile.getTargetLocations(),
        profile.isOpenToRemote(),
        profile.getCoursework(),
        profile.getAreasOfInterest());
  }

  static ProfileDto emptyProfile() {
    return new ProfileDto(
        null, null, null, null, null, null, null, null, List.of(), List.of(), true, List.of(),
        List.of());
  }

  static EducationResponse toResponse(EducationEntry entry) {
    return new EducationResponse(
        entry.getId(),
        entry.getDegree(),
        entry.getInstitution(),
        entry.getUniversity(),
        entry.getSpecialization(),
        entry.getStartYear(),
        entry.getEndYear(),
        entry.getScoreType(),
        entry.getScoreValue(),
        entry.getDisplayOrder());
  }

  static SkillGroupDto toDto(SkillGroup group) {
    return new SkillGroupDto(
        group.getId(), group.getName(), group.getSkills(), group.getDisplayOrder());
  }

  static HighlightResponse toResponse(ProfileHighlight highlight) {
    return new HighlightResponse(
        highlight.getId(),
        highlight.getType(),
        highlight.getText(),
        highlight.getYearLabel(),
        highlight.getDisplayOrder());
  }
}
