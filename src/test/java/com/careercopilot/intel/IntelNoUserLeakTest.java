package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.intel.dto.CompanyIntelDetail;
import com.careercopilot.intel.dto.CompanyIntelSummary;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Structural guardrail: the shared entity and its public DTOs must never gain a field that could
 * identify the contributing user, no matter what future changes are made to this feature.
 */
class IntelNoUserLeakTest {

  private static final List<String> FORBIDDEN_SUBSTRINGS =
      List.of("userid", "applicationid", "email", "username", "interviewdate");

  @Test
  void companyIntelEntity_hasNoUserOrApplicationFields() {
    assertNoForbiddenFields(CompanyIntelEntry.class);
  }

  @Test
  void companyIntelSummaryDto_hasNoUserOrApplicationFields() {
    assertNoForbiddenFields(CompanyIntelSummary.class);
  }

  @Test
  void companyIntelDetailDto_hasNoUserOrApplicationFields() {
    assertNoForbiddenFields(CompanyIntelDetail.class);
    assertNoForbiddenFields(CompanyIntelDetail.RoundSummary.class);
  }

  private void assertNoForbiddenFields(Class<?> type) {
    for (Field field : type.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }
      String lowerName = field.getName().toLowerCase(Locale.ROOT);
      assertThat(FORBIDDEN_SUBSTRINGS)
          .as("field %s on %s must not be user-identifying", field.getName(), type.getSimpleName())
          .noneMatch(lowerName::contains);
    }
  }
}
