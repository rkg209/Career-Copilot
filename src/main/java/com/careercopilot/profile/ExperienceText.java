package com.careercopilot.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical text form of an experience entry — the exact string that gets embedded — and its
 * SHA-256 hash used for change detection (re-embed only when this hash changes).
 */
final class ExperienceText {

  private ExperienceText() {}

  static String build(ExperienceEntry entry) {
    StringBuilder sb = new StringBuilder();
    sb.append('[').append(typeLabel(entry)).append("] ");
    sb.append(entry.getRole()).append(" at ").append(entry.getCompany());
    if (entry.getContext() != null && !entry.getContext().isBlank()) {
      sb.append(" (").append(entry.getContext()).append(')');
    }
    if (entry.getGuide() != null && !entry.getGuide().isBlank()) {
      sb.append("\nGuide: ").append(entry.getGuide());
    }
    if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
      sb.append("\n").append(entry.getDescription());
    }
    for (String achievement : entry.getAchievements()) {
      sb.append("\n- ").append(achievement);
    }
    if (!entry.getSkillsUsed().isEmpty()) {
      sb.append("\nSkills: ").append(String.join(", ", entry.getSkillsUsed()));
    }
    return sb.toString();
  }

  private static String typeLabel(ExperienceEntry entry) {
    CorpusEntryType type =
        entry.getEntryType() != null ? entry.getEntryType() : CorpusEntryType.WORK;
    if (type == CorpusEntryType.PROJECT && entry.getProjectType() != null) {
      return entry.getProjectType() + " PROJECT";
    }
    return type.name().replace('_', ' ');
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
