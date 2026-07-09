package com.careercopilot.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileHighlightRepository extends JpaRepository<ProfileHighlight, Long> {

  List<ProfileHighlight> findByUserIdOrderByDisplayOrderAscIdAsc(UUID userId);

  List<ProfileHighlight> findByUserIdAndTypeOrderByDisplayOrderAscIdAsc(
      UUID userId, HighlightType type);
}
