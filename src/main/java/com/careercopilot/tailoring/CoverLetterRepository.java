package com.careercopilot.tailoring;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

  Page<CoverLetter> findByUserId(UUID userId, Pageable pageable);
}
