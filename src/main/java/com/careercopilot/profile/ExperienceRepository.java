package com.careercopilot.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceRepository extends JpaRepository<ExperienceEntry, Long> {

  List<ExperienceEntry> findByUserIdOrderByStartDateDesc(UUID userId);

  List<ExperienceEntry> findByEmbeddedAtIsNull(Limit limit);

  /**
   * Retrieval primitive for Phase 3: top-k cosine-nearest entries, strictly scoped to the owner.
   * {@code cosine_distance} is provided by hibernate-vector and renders as pgvector's {@code <=>},
   * so the HNSW index is used.
   */
  @Query(
      "SELECT e FROM ExperienceEntry e WHERE e.userId = :userId AND e.embedding IS NOT NULL "
          + "ORDER BY cosine_distance(e.embedding, :queryVector)")
  List<ExperienceEntry> findTopKByVector(
      @Param("userId") UUID userId, @Param("queryVector") float[] queryVector, Limit limit);
}
