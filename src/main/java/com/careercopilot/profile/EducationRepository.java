package com.careercopilot.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationRepository extends JpaRepository<EducationEntry, Long> {

  List<EducationEntry> findByUserIdOrderByDisplayOrderAscIdAsc(UUID userId);
}
