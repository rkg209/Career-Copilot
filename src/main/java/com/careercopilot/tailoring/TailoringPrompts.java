package com.careercopilot.tailoring;

import com.careercopilot.jobs.JobPosting;
import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.ExperienceEntry;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Prompt assembly for the tailoring tasks. Prompts request structured data only — never LaTeX,
 * never formatting instructions (CLAUDE.md resume rule). The JD text must already be
 * injection-fenced by the caller before it gets here.
 */
final class TailoringPrompts {

  private static final DateTimeFormatter MONTH_YEAR =
      DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

  private TailoringPrompts() {}

  static final String RESUME_SYSTEM_PROMPT =
      """
      You produce ONLY structured resume JSON matching the provided schema. You may use ONLY the \
      supplied experience entries verbatim in substance; never invent employers, titles, dates, \
      metrics, or skills. Every experience item must carry the sourceEntryId of the entry it came \
      from, and its company must match that entry exactly. No LaTeX, no markdown, no prose \
      outside JSON.""";

  static final String COVER_LETTER_SYSTEM_PROMPT =
      """
      You produce ONLY structured cover-letter JSON matching the provided schema. Any experience \
      or skills you mention must come from the supplied experience entries; never invent \
      employers, titles, dates, metrics, or skills. No LaTeX, no markdown, no prose outside \
      JSON.""";

  static String resumeUserPrompt(
      String candidateName,
      CandidateProfile profile,
      JobPosting job,
      String fencedJd,
      List<ExperienceEntry> entries) {
    return """
        Candidate name (use verbatim as candidateName): %s
        Profile: %s

        Target job: title=%s, company=%s
        %s

        Candidate experience (the ONLY allowed source; reference entries by id):
        %s

        Return JSON conforming to the ResumeJsonPayload schema:
        - experience[].sourceEntryId = the id of the source entry
        - experience[].title/company/startDate/endDate copied from the source entry \
        (dates as "MMM yyyy", endDate "Present" for current roles)
        - bullets rephrase the entry's description/achievements for relevance to the target job
        - skills only from the entries' skills, ordered by relevance to the job
        - Do NOT include contactInfo (it is added later)
        """
        .formatted(
            candidateName,
            profileBlock(profile),
            job.getTitle(),
            job.getCompany(),
            fencedJd,
            experienceBlock(entries));
  }

  static String coverLetterUserPrompt(
      String candidateName,
      CandidateProfile profile,
      JobPosting job,
      String fencedJd,
      List<ExperienceEntry> entries,
      String tone) {
    return """
        Candidate name (use verbatim as candidateName): %s
        Profile: %s
        Desired tone: %s

        Target job: title=%s, company=%s
        %s

        Candidate experience (the ONLY allowed source of claims):
        %s

        Return JSON conforming to the CoverLetterJsonPayload schema: salutation, 2-4 \
        bodyParagraphs grounded in the experience above, closing. Set recipientCompany and \
        jobTitle from the target job.
        """
        .formatted(
            candidateName,
            profileBlock(profile),
            tone,
            job.getTitle(),
            job.getCompany(),
            fencedJd,
            experienceBlock(entries));
  }

  private static String profileBlock(CandidateProfile profile) {
    if (profile == null) {
      return "(no profile provided)";
    }
    return "headline=%s; summary=%s"
        .formatted(nullSafe(profile.getHeadline()), nullSafe(profile.getSummary()));
  }

  private static String experienceBlock(List<ExperienceEntry> entries) {
    StringBuilder sb = new StringBuilder();
    for (ExperienceEntry e : entries) {
      sb.append("- id=").append(e.getId());
      sb.append(" | role=").append(e.getRole());
      sb.append(" | company=").append(e.getCompany());
      sb.append(" | ").append(e.getStartDate().format(MONTH_YEAR));
      sb.append(" - ")
          .append(
              e.isCurrent() || e.getEndDate() == null
                  ? "Present"
                  : e.getEndDate().format(MONTH_YEAR));
      if (e.getDescription() != null && !e.getDescription().isBlank()) {
        sb.append("\n  description: ").append(e.getDescription());
      }
      if (!e.getAchievements().isEmpty()) {
        sb.append("\n  achievements: ").append(String.join("; ", e.getAchievements()));
      }
      if (!e.getSkillsUsed().isEmpty()) {
        sb.append("\n  skills: ").append(String.join(", ", e.getSkillsUsed()));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
