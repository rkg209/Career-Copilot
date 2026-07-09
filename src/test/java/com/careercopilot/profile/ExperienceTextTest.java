package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperienceTextTest {

  private static ExperienceEntry entry(CorpusEntryType type) {
    ExperienceEntry entry = new ExperienceEntry(UUID.randomUUID());
    entry.setRole("Quadcopter control");
    entry.setCompany("Inter-IIT Team");
    entry.setStartDate(LocalDate.of(2024, 10, 1));
    entry.setEntryType(type);
    entry.setAchievements(List.of("Won Silver"));
    entry.setSkillsUsed(List.of("ROS"));
    return entry;
  }

  @Test
  void build_includesTypeLabel() {
    assertThat(ExperienceText.build(entry(CorpusEntryType.PROJECT))).startsWith("[PROJECT]");
    assertThat(ExperienceText.build(entry(CorpusEntryType.POSITION_OF_RESPONSIBILITY)))
        .startsWith("[POSITION OF RESPONSIBILITY]");
    assertThat(ExperienceText.build(entry(CorpusEntryType.WORK))).startsWith("[WORK]");
  }

  @Test
  void build_includesProjectSubtypeGuideAndContext() {
    ExperienceEntry e = entry(CorpusEntryType.PROJECT);
    e.setProjectType(ProjectType.COURSE);
    e.setGuide("Prof. Vachhani");
    e.setContext("Inter-IIT Tech Meet 2024");
    String text = ExperienceText.build(e);
    assertThat(text)
        .startsWith("[COURSE PROJECT]")
        .contains("(Inter-IIT Tech Meet 2024)")
        .contains("Guide: Prof. Vachhani");
  }

  @Test
  void hash_changesWhenGuideChanges() {
    ExperienceEntry e = entry(CorpusEntryType.PROJECT);
    String before = ExperienceText.sha256(ExperienceText.build(e));
    e.setGuide("Prof. Vachhani");
    String after = ExperienceText.sha256(ExperienceText.build(e));
    assertThat(after).isNotEqualTo(before);
  }
}
