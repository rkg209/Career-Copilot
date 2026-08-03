package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.tracker.InterviewRoundType;
import com.careercopilot.trainer.dto.DueCard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewQueueServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final MockQuestionRepository mockQuestionRepository = mock(MockQuestionRepository.class);
  private final ReviewCardRepository reviewCardRepository = mock(ReviewCardRepository.class);

  private final ReviewQueueService service =
      new ReviewQueueService(userRepository, mockQuestionRepository, reviewCardRepository, 2);

  private final UUID userId = UUID.randomUUID();

  private void stubUser() {
    User user = new User("candidate@example.com", "hash");
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(user));
  }

  @Test
  void dueQueue_dueCardsComeBeforeNewBackfill() {
    stubUser();

    ReviewCard dueCard = new ReviewCard(userId, 1L);
    ReflectionTestUtils.setField(dueCard, "dueAt", Instant.now().minusSeconds(60));
    when(reviewCardRepository.findDue(eq(userId), any(), any(Pageable.class)))
        .thenReturn(List.of(dueCard));
    when(mockQuestionRepository.findByIdIn(List.of(1L)))
        .thenReturn(List.of(questionWithId(1L, "Explain X")));
    when(reviewCardRepository.countByUserIdAndCreatedAtAfter(eq(userId), any())).thenReturn(0L);
    when(reviewCardRepository.findMockQuestionIdsByUserId(userId)).thenReturn(List.of(1L));
    when(mockQuestionRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(questionWithId(1L, "Explain X"), questionWithId(2L, "Explain Y")));

    List<DueCard> queue = service.dueQueue("candidate@example.com", 5);

    assertThat(queue).hasSize(2);
    assertThat(queue.get(0).mockQuestionId()).isEqualTo(1L);
    assertThat(queue.get(0).isNew()).isFalse();
    assertThat(queue.get(1).mockQuestionId()).isEqualTo(2L);
    assertThat(queue.get(1).isNew()).isTrue();
  }

  @Test
  void dueQueue_newBackfillHonoursDailyCap() {
    stubUser();

    when(reviewCardRepository.findDue(eq(userId), any(), any(Pageable.class)))
        .thenReturn(List.of());
    when(reviewCardRepository.countByUserIdAndCreatedAtAfter(eq(userId), any())).thenReturn(2L);
    when(reviewCardRepository.findMockQuestionIdsByUserId(userId)).thenReturn(List.of());
    when(mockQuestionRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(questionWithId(1L, "Explain X"), questionWithId(2L, "Explain Y")));

    // dailyNewLimit is 2 in this test's service instance, cardsCreatedToday is already 2 -> no
    // room.
    List<DueCard> queue = service.dueQueue("candidate@example.com", 20);

    assertThat(queue).isEmpty();
  }

  private MockQuestion questionWithId(Long id, String text) {
    MockQuestion q =
        new MockQuestion(
            userId,
            null,
            null,
            InterviewRoundType.TECHNICAL,
            text,
            MockQuestionSource.OWN_LOG,
            null);
    ReflectionTestUtils.setField(q, "id", id);
    ReflectionTestUtils.setField(q, "createdAt", Instant.now());
    return q;
  }
}
