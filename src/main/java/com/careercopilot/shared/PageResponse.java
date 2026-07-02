package com.careercopilot.shared;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * API pagination envelope: {@code { content, page: { number, size, totalElements, totalPages } } }.
 */
public record PageResponse<T>(List<T> content, PageMeta page) {

  public record PageMeta(int number, int size, long totalElements, int totalPages) {}

  public static <E, T> PageResponse<T> of(Page<E> page, List<T> content) {
    return new PageResponse<>(
        content,
        new PageMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
  }
}
