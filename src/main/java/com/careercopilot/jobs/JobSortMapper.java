package com.careercopilot.jobs;

import com.careercopilot.shared.BadRequestException;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Translates the API sort vocabulary ({@code postedAt,createdAt,title,company}) into either JPA
 * entity property names (for the derived list query) or physical column names (for the native
 * search query). Unknown sort fields are rejected with a 400.
 */
final class JobSortMapper {

  private static final Map<String, String> TO_ENTITY =
      Map.of(
          "postedAt", "postedAt",
          "createdAt", "ingestedAt",
          "title", "title",
          "company", "company");

  private static final Map<String, String> TO_COLUMN =
      Map.of(
          "postedAt", "posted_at",
          "createdAt", "ingested_at",
          "title", "title",
          "company", "company");

  private JobSortMapper() {}

  static Sort listSort(String sortParam) {
    return build(sortParam, TO_ENTITY);
  }

  static Sort searchSort(String sortParam) {
    return build(sortParam, TO_COLUMN);
  }

  private static Sort build(String sortParam, Map<String, String> mapping) {
    String param = (sortParam == null || sortParam.isBlank()) ? "postedAt,desc" : sortParam;
    String[] parts = param.split(",");
    String field = parts[0].trim();
    String mapped = mapping.get(field);
    if (mapped == null) {
      throw new BadRequestException("Unsupported sort field: " + field);
    }
    Sort.Direction dir =
        (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(dir, mapped);
  }
}
