package com.careercopilot.profile;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.profile.dto.ExperienceRequest;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.ProfileDto;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

  private final CandidateProfileRepository profileRepository;
  private final ExperienceRepository experienceRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ProfileService(
      CandidateProfileRepository profileRepository,
      ExperienceRepository experienceRepository,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher) {
    this.profileRepository = profileRepository;
    this.experienceRepository = experienceRepository;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public ProfileDto getProfile(String email) {
    UUID userId = resolveUserId(email);
    return profileRepository
        .findByUserId(userId)
        .map(ExperienceMapper::toDto)
        .orElseGet(ExperienceMapper::emptyProfile);
  }

  public ProfileDto upsertProfile(String email, ProfileDto dto) {
    UUID userId = resolveUserId(email);
    CandidateProfile profile =
        profileRepository.findByUserId(userId).orElseGet(() -> new CandidateProfile(userId));
    profile.setHeadline(dto.headline());
    profile.setSummary(dto.summary());
    profile.setLocation(dto.location());
    profile.setLinkedinUrl(dto.linkedinUrl());
    profile.setGithubUrl(dto.githubUrl());
    profile.setPortfolioUrl(dto.portfolioUrl());
    profile.setTargetRoles(dto.targetRoles() != null ? dto.targetRoles() : new ArrayList<>());
    profile.setTargetLocations(
        dto.targetLocations() != null ? dto.targetLocations() : new ArrayList<>());
    profile.setOpenToRemote(dto.openToRemote() == null || dto.openToRemote());
    return ExperienceMapper.toDto(profileRepository.save(profile));
  }

  @Transactional(readOnly = true)
  public List<ExperienceResponse> listExperience(String email) {
    UUID userId = resolveUserId(email);
    return experienceRepository.findByUserIdOrderByStartDateDesc(userId).stream()
        .map(ExperienceMapper::toResponse)
        .toList();
  }

  public ExperienceResponse addExperience(String email, ExperienceRequest request) {
    UUID userId = resolveUserId(email);
    validateDates(request);
    ExperienceEntry entry = new ExperienceEntry(userId);
    applyRequest(entry, request);
    ExperienceEntry saved = experienceRepository.save(entry);
    eventPublisher.publishEvent(new ExperienceChangedEvent(saved.getId()));
    return ExperienceMapper.toResponse(saved);
  }

  public ExperienceResponse updateExperience(String email, Long id, ExperienceRequest request) {
    UUID userId = resolveUserId(email);
    validateDates(request);
    ExperienceEntry entry = ownedEntry(userId, id);
    applyRequest(entry, request);
    String newHash = ExperienceText.sha256(ExperienceText.build(entry));
    if (!newHash.equals(entry.getTextHash())) {
      entry.setEmbedding(null);
      entry.setTextHash(null);
      entry.setEmbeddedAt(null);
      eventPublisher.publishEvent(new ExperienceChangedEvent(entry.getId()));
    }
    return ExperienceMapper.toResponse(experienceRepository.save(entry));
  }

  public void deleteExperience(String email, Long id) {
    UUID userId = resolveUserId(email);
    experienceRepository.delete(ownedEntry(userId, id));
  }

  private ExperienceEntry ownedEntry(UUID userId, Long id) {
    ExperienceEntry entry =
        experienceRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Experience entry not found: " + id));
    if (!entry.getUserId().equals(userId)) {
      throw new ForbiddenException("Experience entry belongs to another user");
    }
    return entry;
  }

  private UUID resolveUserId(String email) {
    return userRepository
        .findByEmail(email)
        .map(User::getId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }

  private static void validateDates(ExperienceRequest request) {
    boolean isCurrent = Boolean.TRUE.equals(request.isCurrent());
    if (isCurrent && request.endDate() != null) {
      throw new BadRequestException("A current role cannot have an endDate");
    }
    if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
      throw new BadRequestException("endDate must be on or after startDate");
    }
  }

  private static void applyRequest(ExperienceEntry entry, ExperienceRequest request) {
    entry.setRole(request.role());
    entry.setCompany(request.company());
    entry.setLocation(request.location());
    entry.setStartDate(request.startDate());
    entry.setEndDate(request.endDate());
    entry.setCurrent(Boolean.TRUE.equals(request.isCurrent()));
    entry.setDescription(request.description());
    entry.setAchievements(
        request.achievements() != null ? request.achievements() : new ArrayList<>());
    entry.setSkillsUsed(request.skillsUsed() != null ? request.skillsUsed() : new ArrayList<>());
  }
}
