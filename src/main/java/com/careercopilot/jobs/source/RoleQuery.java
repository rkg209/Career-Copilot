package com.careercopilot.jobs.source;

import java.util.List;

/**
 * A single role-driven search request handed to a {@link QueryableJobSource}. {@code roleKey} is
 * the preset key results are tagged with; {@code keywords} are the role's search terms; {@code
 * location} is one configured search location (e.g. "India", "Remote").
 */
public record RoleQuery(String roleKey, List<String> keywords, String location) {}
