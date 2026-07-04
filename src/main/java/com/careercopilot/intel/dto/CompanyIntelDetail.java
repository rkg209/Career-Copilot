package com.careercopilot.intel.dto;

import com.careercopilot.tracker.InterviewRoundType;
import java.util.List;

public record CompanyIntelDetail(String company, List<RoundSummary> rounds) {

  public record RoundSummary(
      InterviewRoundType roundType,
      String roleCategory,
      List<String> questions,
      String processNotes,
      Integer difficulty,
      int entryCount) {}
}
