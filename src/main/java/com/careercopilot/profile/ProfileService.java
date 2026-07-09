package com.careercopilot.profile;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.profile.dto.EducationRequest;
import com.careercopilot.profile.dto.EducationResponse;
import com.careercopilot.profile.dto.ExperienceRequest;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.HighlightRequest;
import com.careercopilot.profile.dto.HighlightResponse;
import com.careercopilot.profile.dto.ProfileDto;
import com.careercopilot.profile.dto.SkillGroupDto;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

  private final CandidateProfileRepository profileRepository;
  private final ExperienceRepository experienceRepository;
  private final EducationRepository educationRepository;
  private final SkillGroupRepository skillGroupRepository;
  private final ProfileHighlightRepository highlightRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ProfileService(
      CandidateProfileRepository profileRepository,
      ExperienceRepository experienceRepository,
      EducationRepository educationRepository,
      SkillGroupRepository skillGroupRepository,
      ProfileHighlightRepository highlightRepository,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher) {
    this.profileRepository = profileRepository;
    this.experienceRepository = experienceRepository;
    this.educationRepository = educationRepository;
    this.skillGroupRepository = skillGroupRepository;
    this.highlightRepository = highlightRepository;
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
    profile.setFullName(dto.fullName());
    profile.setPhone(dto.phone());
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
    profile.setCoursework(dto.coursework() != null ? dto.coursework() : new ArrayList<>());
    profile.setAreasOfInterest(
        dto.areasOfInterest() != null ? dto.areasOfInterest() : new ArrayList<>());
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

  @Transactional(readOnly = true)
  public List<EducationResponse> listEducation(String email) {
    UUID userId = resolveUserId(email);
    return educationRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId).stream()
        .map(ExperienceMapper::toResponse)
        .toList();
  }

  public EducationResponse addEducation(String email, EducationRequest request) {
    UUID userId = resolveUserId(email);
    validateScore(request);
    EducationEntry entry = new EducationEntry(userId);
    applyRequest(entry, request);
    return ExperienceMapper.toResponse(educationRepository.save(entry));
  }

  public EducationResponse updateEducation(String email, Long id, EducationRequest request) {
    UUID userId = resolveUserId(email);
    validateScore(request);
    EducationEntry entry = ownedEducation(userId, id);
    applyRequest(entry, request);
    return ExperienceMapper.toResponse(educationRepository.save(entry));
  }

  public void deleteEducation(String email, Long id) {
    UUID userId = resolveUserId(email);
    educationRepository.delete(ownedEducation(userId, id));
  }

  @Transactional(readOnly = true)
  public List<SkillGroupDto> listSkillGroups(String email) {
    UUID userId = resolveUserId(email);
    return skillGroupRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId).stream()
        .map(ExperienceMapper::toDto)
        .toList();
  }

  /** Bulk replace: the payload becomes the user's complete set of skill groups. */
  public List<SkillGroupDto> replaceSkillGroups(String email, List<SkillGroupDto> groups) {
    UUID userId = resolveUserId(email);
    Set<String> names = new HashSet<>();
    for (SkillGroupDto dto : groups) {
      if (dto.name() == null || dto.name().isBlank()) {
        throw new BadRequestException("Skill group name must not be blank");
      }
      if (!names.add(dto.name().trim().toLowerCase())) {
        throw new BadRequestException("Duplicate skill group name: " + dto.name());
      }
    }
    skillGroupRepository.deleteByUserId(userId);
    skillGroupRepository.flush();
    List<SkillGroup> saved = new ArrayList<>();
    int order = 0;
    for (SkillGroupDto dto : groups) {
      SkillGroup group = new SkillGroup(userId);
      group.setName(dto.name().trim());
      group.setSkills(dto.skills() != null ? dto.skills() : new ArrayList<>());
      group.setDisplayOrder(dto.displayOrder() != null ? dto.displayOrder() : order);
      saved.add(skillGroupRepository.save(group));
      order++;
    }
    return saved.stream().map(ExperienceMapper::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<HighlightResponse> listHighlights(String email, HighlightType type) {
    UUID userId = resolveUserId(email);
    List<ProfileHighlight> highlights =
        type == null
            ? highlightRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId)
            : highlightRepository.findByUserIdAndTypeOrderByDisplayOrderAscIdAsc(userId, type);
    return highlights.stream().map(ExperienceMapper::toResponse).toList();
  }

  public HighlightResponse addHighlight(String email, HighlightRequest request) {
    UUID userId = resolveUserId(email);
    ProfileHighlight highlight = new ProfileHighlight(userId);
    applyRequest(highlight, request);
    return ExperienceMapper.toResponse(highlightRepository.save(highlight));
  }

  public HighlightResponse updateHighlight(String email, Long id, HighlightRequest request) {
    UUID userId = resolveUserId(email);
    ProfileHighlight highlight = ownedHighlight(userId, id);
    applyRequest(highlight, request);
    return ExperienceMapper.toResponse(highlightRepository.save(highlight));
  }

  public void deleteHighlight(String email, Long id) {
    UUID userId = resolveUserId(email);
    highlightRepository.delete(ownedHighlight(userId, id));
  }

  private EducationEntry ownedEducation(UUID userId, Long id) {
    EducationEntry entry =
        educationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Education entry not found: " + id));
    if (!entry.getUserId().equals(userId)) {
      throw new ForbiddenException("Education entry belongs to another user");
    }
    return entry;
  }

  private ProfileHighlight ownedHighlight(UUID userId, Long id) {
    ProfileHighlight highlight =
        highlightRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Highlight not found: " + id));
    if (!highlight.getUserId().equals(userId)) {
      throw new ForbiddenException("Highlight belongs to another user");
    }
    return highlight;
  }

  private static void validateScore(EducationRequest request) {
    if (request.scoreValue() != null
        && !request.scoreValue().isBlank()
        && request.scoreType() == null) {
      throw new BadRequestException("scoreType is required when scoreValue is present");
    }
  }

  private static void applyRequest(EducationEntry entry, EducationRequest request) {
    entry.setDegree(request.degree());
    entry.setInstitution(request.institution());
    entry.setUniversity(request.university());
    entry.setSpecialization(request.specialization());
    entry.setStartYear(request.startYear());
    entry.setEndYear(request.endYear());
    entry.setScoreType(request.scoreType());
    entry.setScoreValue(request.scoreValue());
    entry.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
  }

  private static void applyRequest(ProfileHighlight highlight, HighlightRequest request) {
    highlight.setType(request.type());
    highlight.setText(request.text());
    highlight.setYearLabel(request.yearLabel());
    highlight.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
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
    CorpusEntryType entryType =
        request.entryType() != null ? request.entryType() : CorpusEntryType.WORK;
    if (request.projectType() != null && entryType != CorpusEntryType.PROJECT) {
      throw new BadRequestException("projectType is only allowed when entryType is PROJECT");
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
    entry.setEntryType(request.entryType() != null ? request.entryType() : CorpusEntryType.WORK);
    entry.setProjectType(request.projectType());
    entry.setGuide(request.guide());
    entry.setContext(request.context());
    entry.setUrl(request.url());
  }
}
