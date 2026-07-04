package com.careercopilot.intel.dto;

import com.careercopilot.tracker.InterviewRoundType;
import java.util.List;

public record CompanyIntelSummary(
    String company, List<InterviewRoundType> roundTypes, int entryCount, Double avgDifficulty) {}
