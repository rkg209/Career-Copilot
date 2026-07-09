package com.careercopilot.profile;

import com.careercopilot.profile.dto.EducationRequest;
import com.careercopilot.profile.dto.EducationResponse;
import com.careercopilot.profile.dto.ExperienceRequest;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.HighlightRequest;
import com.careercopilot.profile.dto.HighlightResponse;
import com.careercopilot.profile.dto.ProfileDto;
import com.careercopilot.profile.dto.SkillGroupDto;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping
  public ProfileDto getProfile(Principal principal) {
    return profileService.getProfile(principal.getName());
  }

  @PutMapping
  public ProfileDto upsertProfile(@RequestBody ProfileDto dto, Principal principal) {
    return profileService.upsertProfile(principal.getName(), dto);
  }

  @GetMapping("/experience")
  public List<ExperienceResponse> listExperience(Principal principal) {
    return profileService.listExperience(principal.getName());
  }

  @PostMapping("/experience")
  public ResponseEntity<ExperienceResponse> addExperience(
      @Valid @RequestBody ExperienceRequest request, Principal principal) {
    ExperienceResponse created = profileService.addExperience(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/experience/{id}")
  public ExperienceResponse updateExperience(
      @PathVariable Long id, @Valid @RequestBody ExperienceRequest request, Principal principal) {
    return profileService.updateExperience(principal.getName(), id, request);
  }

  @DeleteMapping("/experience/{id}")
  public ResponseEntity<Void> deleteExperience(@PathVariable Long id, Principal principal) {
    profileService.deleteExperience(principal.getName(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/education")
  public List<EducationResponse> listEducation(Principal principal) {
    return profileService.listEducation(principal.getName());
  }

  @PostMapping("/education")
  public ResponseEntity<EducationResponse> addEducation(
      @Valid @RequestBody EducationRequest request, Principal principal) {
    EducationResponse created = profileService.addEducation(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/education/{id}")
  public EducationResponse updateEducation(
      @PathVariable Long id, @Valid @RequestBody EducationRequest request, Principal principal) {
    return profileService.updateEducation(principal.getName(), id, request);
  }

  @DeleteMapping("/education/{id}")
  public ResponseEntity<Void> deleteEducation(@PathVariable Long id, Principal principal) {
    profileService.deleteEducation(principal.getName(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/skill-groups")
  public List<SkillGroupDto> listSkillGroups(Principal principal) {
    return profileService.listSkillGroups(principal.getName());
  }

  @PutMapping("/skill-groups")
  public List<SkillGroupDto> replaceSkillGroups(
      @RequestBody List<@Valid SkillGroupDto> groups, Principal principal) {
    return profileService.replaceSkillGroups(principal.getName(), groups);
  }

  @GetMapping("/highlights")
  public List<HighlightResponse> listHighlights(
      @RequestParam(required = false) HighlightType type, Principal principal) {
    return profileService.listHighlights(principal.getName(), type);
  }

  @PostMapping("/highlights")
  public ResponseEntity<HighlightResponse> addHighlight(
      @Valid @RequestBody HighlightRequest request, Principal principal) {
    HighlightResponse created = profileService.addHighlight(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/highlights/{id}")
  public HighlightResponse updateHighlight(
      @PathVariable Long id, @Valid @RequestBody HighlightRequest request, Principal principal) {
    return profileService.updateHighlight(principal.getName(), id, request);
  }

  @DeleteMapping("/highlights/{id}")
  public ResponseEntity<Void> deleteHighlight(@PathVariable Long id, Principal principal) {
    profileService.deleteHighlight(principal.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
