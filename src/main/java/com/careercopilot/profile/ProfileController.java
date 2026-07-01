package com.careercopilot.profile;

import com.careercopilot.profile.dto.ExperienceRequest;
import com.careercopilot.profile.dto.ExperienceResponse;
import com.careercopilot.profile.dto.ProfileDto;
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
}
