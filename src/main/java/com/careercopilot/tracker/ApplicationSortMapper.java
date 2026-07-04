package com.careercopilot.tracker;

import com.careercopilot.shared.BadRequestException;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Translates the API sort vocabulary ({@code createdAt,updatedAt,appliedAt,status,companyName})
 * into entity property names. Unknown sort fields are rejected with a 400.
 */
final class ApplicationSortMapper {

  private static final Map<String, String> TO_ENTITY =
      Map.of(
          "createdAt", "createdAt",
          "updatedAt", "updatedAt",
          "appliedAt", "appliedAt",
          "status", "status",
          "companyName", "companySnapshot");

  private ApplicationSortMapper() {}

  static Sort listSort(String sortParam) {
    String param = (sortParam == null || sortParam.isBlank()) ? "updatedAt,desc" : sortParam;
    String[] parts = param.split(",");
    String field = parts[0].trim();
    String mapped = TO_ENTITY.get(field);
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
