package com.careercopilot.trainer;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.trainer.dto.DueCard;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the "due today" practice queue: cards actually due first (weakest/oldest), then
 * never-answered questions backfilled up to the daily new-card cap (Anki semantics).
 */
@Service
@Transactional(readOnly = true)
public class ReviewQueueService {

  private final UserRepository userRepository;
  private final MockQuestionRepository mockQuestionRepository;
  private final ReviewCardRepository reviewCardRepository;
  private final int dailyNewLimit;

  public ReviewQueueService(
      UserRepository userRepository,
      MockQuestionRepository mockQuestionRepository,
      ReviewCardRepository reviewCardRepository,
      @Value("${careercopilot.trainer.daily-new-limit:20}") int dailyNewLimit) {
    this.userRepository = userRepository;
    this.mockQuestionRepository = mockQuestionRepository;
    this.reviewCardRepository = reviewCardRepository;
    this.dailyNewLimit = dailyNewLimit;
  }

  public List<DueCard> dueQueue(String email, int limit) {
    User user = resolveUser(email);
    UUID userId = user.getId();
    Instant now = Instant.now();

    List<ReviewCard> dueCards = reviewCardRepository.findDue(userId, now, PageRequest.of(0, limit));
    List<DueCard> result = new ArrayList<>();
    if (!dueCards.isEmpty()) {
      List<Long> questionIds = dueCards.stream().map(ReviewCard::getMockQuestionId).toList();
      var questionTextById =
          mockQuestionRepository.findByIdIn(questionIds).stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      MockQuestion::getId, MockQuestion::getQuestionText));
      for (ReviewCard card : dueCards) {
        result.add(
            new DueCard(
                card.getMockQuestionId(),
                questionTextById.get(card.getMockQuestionId()),
                card.getDueAt(),
                card.getRepetitions(),
                card.getEaseFactor().doubleValue(),
                false));
      }
    }

    int remainingSlots = limit - result.size();
    if (remainingSlots > 0) {
      long cardsCreatedToday =
          reviewCardRepository.countByUserIdAndCreatedAtAfter(userId, startOfUtcDay(now));
      int dailyRemaining = (int) Math.max(0, dailyNewLimit - cardsCreatedToday);
      int backfillCount = Math.min(remainingSlots, dailyRemaining);
      if (backfillCount > 0) {
        Set<Long> withCards =
            new HashSet<>(reviewCardRepository.findMockQuestionIdsByUserId(userId));
        int added = 0;
        for (MockQuestion question :
            mockQuestionRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
          if (added >= backfillCount) {
            break;
          }
          if (withCards.contains(question.getId())) {
            continue;
          }
          result.add(
              new DueCard(
                  question.getId(),
                  question.getQuestionText(),
                  question.getCreatedAt(),
                  0,
                  Sm2Scheduler.INITIAL_EF.doubleValue(),
                  true));
          added++;
        }
      }
    }
    return result;
  }

  private static Instant startOfUtcDay(Instant now) {
    return now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }
}
