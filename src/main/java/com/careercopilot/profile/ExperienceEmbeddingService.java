package com.careercopilot.profile;

import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.EmbeddingUnavailableException;
import com.careercopilot.shared.LlmTask;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Embeds experience entries asynchronously after commit. Embedding failure is never fatal — the
 * entry stays PENDING and the scheduled sweep retries until the provider is reachable.
 */
@Service
public class ExperienceEmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(ExperienceEmbeddingService.class);
  private static final int RETRY_BATCH_SIZE = 50;

  private final ExperienceRepository experienceRepository;
  private final EmbeddingGateway embeddingGateway;

  public ExperienceEmbeddingService(
      ExperienceRepository experienceRepository, EmbeddingGateway embeddingGateway) {
    this.experienceRepository = experienceRepository;
    this.embeddingGateway = embeddingGateway;
  }

  @Async
  @TransactionalEventListener
  public void onExperienceChanged(ExperienceChangedEvent event) {
    embedEntry(event.entryId());
  }

  @Scheduled(cron = "${embedding.retry-cron}")
  public void retryPending() {
    for (ExperienceEntry entry :
        experienceRepository.findByEmbeddedAtIsNull(Limit.of(RETRY_BATCH_SIZE))) {
      embedEntry(entry.getId());
    }
  }

  // Called from the async listener and the sweep (self-invocation), so no @Transactional here —
  // each repository call runs in its own transaction, which is what we want for a best-effort job.
  public void embedEntry(Long entryId) {
    ExperienceEntry entry = experienceRepository.findById(entryId).orElse(null);
    if (entry == null) {
      return; // deleted before the async task ran
    }
    String text = ExperienceText.build(entry);
    String hash = ExperienceText.sha256(text);
    if (entry.getEmbeddedAt() != null && hash.equals(entry.getTextHash())) {
      return; // unchanged — no re-embed
    }
    try {
      float[] vector = embeddingGateway.embed(entry.getUserId(), LlmTask.EMBEDDING, text);
      entry.setEmbedding(vector);
      entry.setTextHash(hash);
      entry.setEmbeddedAt(Instant.now());
      experienceRepository.save(entry);
    } catch (EmbeddingUnavailableException e) {
      log.warn(
          "Embedding unavailable for entry {} — will retry via sweep: {}", entryId, e.getMessage());
    }
  }
}
