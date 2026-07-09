package com.careercopilot.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillGroupRepository extends JpaRepository<SkillGroup, Long> {

  List<SkillGroup> findByUserIdOrderByDisplayOrderAscIdAsc(UUID userId);

  void deleteByUserId(UUID userId);
}
