package com.careercopilot.profile;

import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.ProfileDto;
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
        entry.getEmbeddedAt() != null ? "EMBEDDED" : "PENDING",
        entry.getCreatedAt(),
        entry.getUpdatedAt());
  }

  static ProfileDto toDto(CandidateProfile profile) {
    return new ProfileDto(
        profile.getHeadline(),
        profile.getSummary(),
        profile.getLocation(),
        profile.getLinkedinUrl(),
        profile.getGithubUrl(),
        profile.getPortfolioUrl(),
        profile.getTargetRoles(),
        profile.getTargetLocations(),
        profile.isOpenToRemote());
  }

  static ProfileDto emptyProfile() {
    return new ProfileDto(null, null, null, null, null, null, List.of(), List.of(), true);
  }
}
