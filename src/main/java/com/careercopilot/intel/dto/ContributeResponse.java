package com.careercopilot.intel.dto;

public record ContributeResponse(
    String status, String company, int publishedCountForCompany, int threshold) {}
