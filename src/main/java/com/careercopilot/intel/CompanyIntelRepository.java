package com.careercopilot.intel;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyIntelRepository extends JpaRepository<CompanyIntelEntry, Long> {

  /** Staged + published count for a company — drives the aggregation-threshold check. */
  long countByCompany(String company);

  /** The currently-staged batch for a company, flipped to published once the threshold is met. */
  List<CompanyIntelEntry> findByCompanyAndPublishedFalse(String company);

  List<CompanyIntelEntry> findByCompanyAndPublishedTrue(String company);

  /**
   * Batched form of {@link #findByCompanyAndPublishedTrue} for a whole page of companies at once —
   * avoids an N+1 select when summarizing a page of {@code findDistinctPublishedCompanies}.
   */
  List<CompanyIntelEntry> findByCompanyInAndPublishedTrue(List<String> companies);

  @Query(
      "select distinct e.company from CompanyIntelEntry e where e.published = true order by e.company asc")
  Page<String> findDistinctPublishedCompanies(Pageable pageable);
}
