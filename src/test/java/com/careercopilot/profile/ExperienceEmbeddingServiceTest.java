package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.EmbeddingUnavailableException;
import com.careercopilot.shared.LlmTask;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExperienceEmbeddingServiceTest {

  private ExperienceRepository experienceRepository;
  private EmbeddingGateway embeddingGateway;
  private ExperienceEmbeddingService service;

  @BeforeEach
  void setUp() {
    experienceRepository = mock(ExperienceRepository.class);
    embeddingGateway = mock(EmbeddingGateway.class);
    service = new ExperienceEmbeddingService(experienceRepository, embeddingGateway);
  }

  @Test
  void embedEntry_newEntry_embedsAndStoresHash() {
    ExperienceEntry entry = entry();
    when(experienceRepository.findById(1L)).thenReturn(Optional.of(entry));
    when(embeddingGateway.embed(any(), any(), anyString())).thenReturn(new float[] {0.5f});

    service.embedEntry(1L);

    verify(embeddingGateway).embed(any(UUID.class), any(LlmTask.class), anyString());
    verify(experienceRepository).save(entry);
    assertThat(entry.getEmbedding()).isNotNull();
    assertThat(entry.getTextHash()).hasSize(64);
    assertThat(entry.getEmbeddedAt()).isNotNull();
  }

  @Test
  void embedEntry_unchangedText_skipsEmbedding() {
    ExperienceEntry entry = entry();
    entry.setTextHash(ExperienceText.sha256(ExperienceText.build(entry)));
    entry.setEmbeddedAt(Instant.now());
    when(experienceRepository.findById(1L)).thenReturn(Optional.of(entry));

    service.embedEntry(1L);

    verify(embeddingGateway, never()).embed(any(), any(), anyString());
    verify(experienceRepository, never()).save(any());
  }

  @Test
  void embedEntry_providerUnavailable_leavesEntryPending() {
    ExperienceEntry entry = entry();
    when(experienceRepository.findById(1L)).thenReturn(Optional.of(entry));
    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenThrow(new EmbeddingUnavailableException("down", new RuntimeException()));

    service.embedEntry(1L);

    verify(experienceRepository, never()).save(any());
    assertThat(entry.getEmbeddedAt()).isNull();
  }

  @Test
  void embedEntry_deletedEntry_isNoOp() {
    when(experienceRepository.findById(1L)).thenReturn(Optional.empty());

    service.embedEntry(1L);

    verify(embeddingGateway, never()).embed(any(), any(), anyString());
  }

  private static ExperienceEntry entry() {
    ExperienceEntry entry = new ExperienceEntry(UUID.randomUUID());
    entry.setRole("Backend Engineer");
    entry.setCompany("Acme");
    entry.setStartDate(LocalDate.of(2023, 1, 1));
    entry.setDescription("Built APIs");
    entry.setAchievements(List.of("Cut latency 40%"));
    entry.setSkillsUsed(List.of("Java"));
    try {
      Field field = ExperienceEntry.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entry, 1L);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return entry;
  }
}
