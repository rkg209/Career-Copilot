package com.careercopilot.jobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

  Page<JobPosting> findByActiveTrue(Pageable pageable);

  Optional<JobPosting> findByIdAndActiveTrue(Long id);

  /** Active postings tagged with the given role, via array containment (GIN-indexed). */
  @Query(
      value =
          "SELECT * FROM job_postings j WHERE j.is_active = true "
              + "AND j.matched_roles @> ARRAY[CAST(:role AS text)]",
      countQuery =
          "SELECT count(*) FROM job_postings j WHERE j.is_active = true "
              + "AND j.matched_roles @> ARRAY[CAST(:role AS text)]",
      nativeQuery = true)
  Page<JobPosting> findByRole(@Param("role") String role, Pageable pageable);

  /**
   * Freshness sweep: deactivates active postings whose effective age (posted_at, or ingested_at
   * when posted_at is null) is older than the cutoff. Re-ingestion re-activates a row via {@code
   * merge()}. Returns the number of rows deactivated.
   */
  @Modifying
  @Query(
      "UPDATE JobPosting j SET j.active = false "
          + "WHERE j.active = true AND COALESCE(j.postedAt, j.ingestedAt) < :cutoff")
  int deactivateOlderThan(@Param("cutoff") Instant cutoff);

  /** Idempotent-ingestion lookup; MANUAL_PASTE rows (null externalId) are never matched here. */
  Optional<JobPosting> findBySourceAndExternalId(JobSource source, String externalId);

  /**
   * Most recent ingestion timestamp among scraped postings (excludes {@code excludedSource}, which
   * isn't a scraping event and would otherwise skew "last scraped" toward whenever a user last
   * pasted a job — pass {@code JobSource.MANUAL_PASTE}). Empty if scraping has never completed
   * since this data existed. Uses a bind parameter rather than an inline enum literal: Hibernate
   * renders a JPQL enum literal as a cast to the Java enum's simple name (e.g. {@code
   * ::JobSource}), which doesn't match this DB's actual enum type name ({@code job_source}) and
   * fails at runtime.
   */
  @Query("SELECT MAX(j.ingestedAt) FROM JobPosting j WHERE j.source <> :excludedSource")
  Optional<Instant> findLastScrapedAt(@Param("excludedSource") JobSource excludedSource);

  /**
   * Disappearance-based closed detection: a whole-board adapter fetch is exhaustive, so any
   * previously active posting from that source whose externalId is absent from the latest fetch is
   * no longer listed upstream (closed/filled/removed) and is deactivated. Never called with an
   * empty {@code seenExternalIds} — callers must skip the sweep when a fetch returns nothing, since
   * an empty result is ambiguous with a failed/short-circuited call.
   */
  @Modifying
  @Query(
      "UPDATE JobPosting j SET j.active = false "
          + "WHERE j.active = true AND j.source = :source AND j.externalId IS NOT NULL "
          + "AND j.externalId NOT IN :seenExternalIds")
  int deactivateMissingExternalIds(
      @Param("source") JobSource source,
      @Param("seenExternalIds") Collection<String> seenExternalIds);

  /**
   * Full-text + filter search over active postings. Every optional filter is guarded with an
   * explicit CAST so Postgres can infer the bind-parameter type when the value is null. Sorting is
   * supplied via {@code pageable}; the service maps API sort fields to real column names before
   * calling this.
   */
  @Query(
      value =
          "SELECT * FROM job_postings j WHERE j.is_active = true "
              + "AND (CAST(:q AS text) IS NULL OR to_tsvector('english', "
              + "  coalesce(j.title,'') || ' ' || coalesce(j.company,'') || ' ' || coalesce(j.full_text,'')) "
              + "  @@ plainto_tsquery('english', CAST(:q AS text))) "
              + "AND (CAST(:location AS text) IS NULL OR j.location ILIKE ('%' || CAST(:location AS text) || '%')) "
              + "AND (CAST(:remote AS boolean) IS NULL OR j.remote = CAST(:remote AS boolean)) "
              + "AND (CAST(:employmentType AS text) IS NULL OR j.employment_type = CAST(:employmentType AS text)) "
              + "AND (CAST(:salaryMin AS numeric) IS NULL OR j.salary_min >= CAST(:salaryMin AS numeric)) "
              + "AND (CAST(:postedAfter AS timestamptz) IS NULL OR j.posted_at > CAST(:postedAfter AS timestamptz)) "
              + "AND (CAST(:role AS text) IS NULL OR j.matched_roles @> ARRAY[CAST(:role AS text)])",
      countQuery =
          "SELECT count(*) FROM job_postings j WHERE j.is_active = true "
              + "AND (CAST(:q AS text) IS NULL OR to_tsvector('english', "
              + "  coalesce(j.title,'') || ' ' || coalesce(j.company,'') || ' ' || coalesce(j.full_text,'')) "
              + "  @@ plainto_tsquery('english', CAST(:q AS text))) "
              + "AND (CAST(:location AS text) IS NULL OR j.location ILIKE ('%' || CAST(:location AS text) || '%')) "
              + "AND (CAST(:remote AS boolean) IS NULL OR j.remote = CAST(:remote AS boolean)) "
              + "AND (CAST(:employmentType AS text) IS NULL OR j.employment_type = CAST(:employmentType AS text)) "
              + "AND (CAST(:salaryMin AS numeric) IS NULL OR j.salary_min >= CAST(:salaryMin AS numeric)) "
              + "AND (CAST(:postedAfter AS timestamptz) IS NULL OR j.posted_at > CAST(:postedAfter AS timestamptz)) "
              + "AND (CAST(:role AS text) IS NULL OR j.matched_roles @> ARRAY[CAST(:role AS text)])",
      nativeQuery = true)
  Page<JobPosting> search(
      @Param("q") String q,
      @Param("location") String location,
      @Param("remote") Boolean remote,
      @Param("employmentType") String employmentType,
      @Param("salaryMin") BigDecimal salaryMin,
      @Param("postedAfter") Instant postedAfter,
      @Param("role") String role,
      Pageable pageable);
}
