package com.careercopilot.profile;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

  Optional<CandidateProfile> findByUserId(UUID userId);
}
