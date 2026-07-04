package com.careercopilot.tracker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

  Page<Application> findByUserId(UUID userId, Pageable pageable);

  @Query("select a.id from Application a where a.companySnapshot = :company")
  List<Long> findIdsByCompanySnapshot(@Param("company") String company);
}
